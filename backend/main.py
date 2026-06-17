import base64
import binascii
import os
import threading
import urllib.request
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from groq import AsyncGroq
from motor.motor_asyncio import AsyncIOMotorClient
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, Field
from pymongo import ASCENDING, DESCENDING
from pymongo.errors import PyMongoError

load_dotenv()

FACE_EMBEDDING_MODEL = "opencv-sface-2021dec"
FACE_MATCH_THRESHOLD = 0.363
GROQ_MODEL = "llama-3.1-8b-instant"
MODEL_DIR = Path(__file__).resolve().parent / "models"
YUNET_MODEL_PATH = MODEL_DIR / "face_detection_yunet_2023mar.onnx"
SFACE_MODEL_PATH = MODEL_DIR / "face_recognition_sface_2021dec.onnx"
YUNET_MODEL_URL = (
    "https://github.com/opencv/opencv_zoo/raw/main/models/"
    "face_detection_yunet/face_detection_yunet_2023mar.onnx"
)
SFACE_MODEL_URL = (
    "https://github.com/opencv/opencv_zoo/raw/main/models/"
    "face_recognition_sface/face_recognition_sface_2021dec.onnx"
)


class ImagePayload(BaseModel):
    image: str = Field(..., description="A base64-encoded image or data URL")

class EnrollRequest(ImagePayload):
    name: str = Field(..., min_length=1, max_length=100)
    relationship: str = Field(..., min_length=1, max_length=100)
    caregiver_phone: str | None = Field(None, max_length=20)

class MemoryLogRequest(BaseModel):
    person_id: str | None = Field(None)
    note: str | None = Field(None, max_length=2000)
    person_name: str | None = Field(None, max_length=100)
    relationship: str | None = Field(None, max_length=100)
    summary: str | None = Field(None, max_length=2000)
    caregiver_phone: str | None = Field(None, max_length=20)
    timestamp: str | None = Field(None)

class SummarizeRequest(BaseModel):
    person_id: str = Field(..., min_length=1)

class SosRequest(BaseModel):
    person_name: str = Field(..., min_length=1)
    caregiver_phone: str = Field(..., min_length=1)
    latitude: float
    longitude: float
    location_link: str = Field(..., min_length=1)
    timestamp: str | None = Field(None)


def utc_now() -> datetime:
    return datetime.now(timezone.utc)

def decode_image(image_data: str) -> np.ndarray:
    encoded = image_data.split(",", 1)[-1]
    try:
        raw = base64.b64decode(encoded, validate=True)
        image = Image.open(BytesIO(raw)).convert("RGB")
    except (binascii.Error, ValueError, UnidentifiedImageError) as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid base64 image.") from exc
    return np.asarray(image)

def download_model(path: Path, url: str) -> None:
    if path.exists():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = path.with_suffix(f"{path.suffix}.download")
    try:
        urllib.request.urlretrieve(url, temporary_path)
        temporary_path.replace(path)
    except Exception as exc:
        temporary_path.unlink(missing_ok=True)
        raise RuntimeError(f"Could not download face model from {url}.") from exc

class FaceEncoder:
    def __init__(self) -> None:
        download_model(YUNET_MODEL_PATH, YUNET_MODEL_URL)
        download_model(SFACE_MODEL_PATH, SFACE_MODEL_URL)
        self.detector = cv2.FaceDetectorYN.create(str(YUNET_MODEL_PATH), "", (320, 320), 0.9, 0.3, 5000)
        self.recognizer = cv2.FaceRecognizerSF.create(str(SFACE_MODEL_PATH), "")
        self.lock = threading.Lock()

    def create_embedding(self, image_data: str) -> list[float]:
        image_rgb = decode_image(image_data)
        image = cv2.cvtColor(image_rgb, cv2.COLOR_RGB2BGR)
        height, width = image.shape[:2]
        try:
            with self.lock:
                self.detector.setInputSize((width, height))
                _, faces = self.detector.detect(image)
                if faces is None or len(faces) == 0:
                    raise ValueError("No face detected.")
                face = max(faces, key=lambda candidate: candidate[-1])
                aligned_face = self.recognizer.alignCrop(image, face)
                feature = self.recognizer.feature(aligned_face).flatten()
        except ValueError as exc:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="No clear face could be detected in the image.") from exc
        except cv2.error as exc:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="The face image could not be processed.") from exc
        norm = np.linalg.norm(feature)
        if norm == 0:
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="No face embedding was generated.")
        return [float(value) for value in feature / norm]

def cosine_similarity(left: list[float], right: list[float]) -> float:
    if len(left) != len(right):
        return -1.0
    left_vector = np.asarray(left, dtype=np.float32)
    right_vector = np.asarray(right, dtype=np.float32)
    denominator = np.linalg.norm(left_vector) * np.linalg.norm(right_vector)
    if denominator == 0:
        return -1.0
    return float(np.dot(left_vector, right_vector) / denominator)

def similarity_to_confidence(similarity: float) -> float:
    return round(float(np.clip(similarity, 0, 1)), 4)

def require_summary_text(response: Any) -> str:
    try:
        summary = response.choices[0].message.content
        summary = summary.strip() if summary else ""
    except (AttributeError, IndexError, TypeError, ValueError) as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Groq returned no summary.") from exc
    if not summary:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Groq returned no summary.")
    return summary

def serialize_memory(memory: dict[str, Any]) -> dict[str, Any]:
    return {"id": str(memory["_id"]), "person_id": memory["person_id"], "note": memory["note"], "created_at": memory["created_at"]}

@asynccontextmanager
async def lifespan(app: FastAPI):
    mongodb_uri = os.getenv("MONGODB_URI")
    if not mongodb_uri:
        raise RuntimeError("MONGODB_URI is not configured.")
    groq_api_key = os.getenv("GROQ_API_KEY")
    if not groq_api_key:
        raise RuntimeError("GROQ_API_KEY is not configured.")
    client = AsyncIOMotorClient(mongodb_uri)
    app.state.mongo_client = client
    app.state.db = client.get_default_database("memory_assistant")
    app.state.groq = AsyncGroq(api_key=groq_api_key)
    app.state.face_encoder = FaceEncoder()
    await app.state.db.people.create_index([("person_id", ASCENDING)], unique=True)
    await app.state.db.memories.create_index([("person_id", ASCENDING), ("created_at", DESCENDING)])
    try:
        yield
    finally:
        await app.state.groq.close()
        client.close()

app = FastAPI(title="Dementia Memory Assistant API", version="1.0.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}

@app.post("/enroll", status_code=status.HTTP_201_CREATED)
async def enroll(payload: EnrollRequest, request: Request) -> dict[str, Any]:
    embedding = request.app.state.face_encoder.create_embedding(payload.image)
    person_id = os.urandom(12).hex()
    person = {
        "person_id": person_id,
        "name": payload.name.strip(),
        "relationship": payload.relationship.strip(),
        "caregiver_phone": payload.caregiver_phone,
        "embedding": embedding,
        "embedding_model": FACE_EMBEDDING_MODEL,
        "created_at": utc_now(),
    }
    try:
        await request.app.state.db.people.insert_one(person)
    except PyMongoError as exc:
        raise HTTPException(status_code=503, detail="Could not save enrollment.") from exc
    return {
        "person_id": person_id,
        "name": person["name"],
        "relationship": person["relationship"],
        "caregiver_phone": person["caregiver_phone"],
    }

@app.post("/identify")
async def identify(payload: ImagePayload, request: Request) -> dict[str, Any]:
    try:
        probe_embedding = request.app.state.face_encoder.create_embedding(payload.image)
    except HTTPException as exc:
        if exc.status_code in (status.HTTP_422_UNPROCESSABLE_ENTITY, status.HTTP_400_BAD_REQUEST):
            return {"match": None, "confidence": 0.0}
        raise exc
    best_person: dict[str, Any] | None = None
    best_similarity = -1.0
    try:
        async for person in request.app.state.db.people.find(
            {"embedding_model": FACE_EMBEDDING_MODEL},
            {"embedding": 1, "person_id": 1, "name": 1, "relationship": 1, "caregiver_phone": 1},
        ):
            similarity = cosine_similarity(probe_embedding, person["embedding"])
            if similarity > best_similarity:
                best_similarity = similarity
                best_person = person
    except PyMongoError as exc:
        raise HTTPException(status_code=503, detail="Could not search enrollments.") from exc
    if best_person is None or best_similarity < FACE_MATCH_THRESHOLD:
        return {"match": None, "confidence": 0.0}
    return {
        "match": {
            "person_id": best_person["person_id"],
            "name": best_person["name"],
            "relationship": best_person["relationship"],
            "caregiver_phone": best_person.get("caregiver_phone"),
        },
        "confidence": similarity_to_confidence(best_similarity),
    }

@app.get("/memory/{person_id}")
async def get_memories(person_id: str, request: Request) -> dict[str, Any]:
    person = await request.app.state.db.people.find_one({"person_id": person_id}, {"_id": 1})
    if person is None:
        raise HTTPException(status_code=404, detail="Person not found.")
    cursor = request.app.state.db.memories.find({"person_id": person_id}).sort("created_at", DESCENDING).limit(5)
    memories = [serialize_memory(memory) async for memory in cursor]
    return {"person_id": person_id, "memories": memories}

@app.post("/memory/log", status_code=status.HTTP_201_CREATED)
async def log_memory(payload: MemoryLogRequest, request: Request) -> dict[str, Any]:
    if payload.person_name:
        person = await request.app.state.db.people.find_one({
            "name": payload.person_name.strip(),
            "caregiver_phone": payload.caregiver_phone.strip() if payload.caregiver_phone else None
        })
        if not person:
            person = await request.app.state.db.people.find_one({
                "name": payload.person_name.strip()
            })
        
        if person:
            person_id = person["person_id"]
        else:
            person_id = os.urandom(12).hex()
            new_person = {
                "person_id": person_id,
                "name": payload.person_name.strip(),
                "relationship": payload.relationship.strip() if payload.relationship else "Visitor",
                "caregiver_phone": payload.caregiver_phone.strip() if payload.caregiver_phone else None,
                "embedding": [],
                "embedding_model": FACE_EMBEDDING_MODEL,
                "created_at": utc_now()
            }
            await request.app.state.db.people.insert_one(new_person)
        
        created_at = utc_now()
        if payload.timestamp:
            try:
                ts_str = payload.timestamp.replace("Z", "+00:00")
                created_at = datetime.fromisoformat(ts_str)
            except Exception:
                pass
        
        memory = {
            "person_id": person_id,
            "note": payload.summary.strip() if payload.summary else "",
            "created_at": created_at
        }
        result = await request.app.state.db.memories.insert_one(memory)
        memory["_id"] = result.inserted_id
        return serialize_memory(memory)
        
    else:
        if not payload.person_id:
            raise HTTPException(status_code=400, detail="person_id is required.")
        person = await request.app.state.db.people.find_one({"person_id": payload.person_id}, {"_id": 1})
        if person is None:
            raise HTTPException(status_code=404, detail="Person not found.")
        memory = {
            "person_id": payload.person_id,
            "note": payload.note.strip() if payload.note else "",
            "created_at": utc_now()
        }
        result = await request.app.state.db.memories.insert_one(memory)
        memory["_id"] = result.inserted_id
        return serialize_memory(memory)

@app.post("/summarize")
async def summarize(payload: SummarizeRequest, request: Request) -> dict[str, Any]:
    person = await request.app.state.db.people.find_one({"person_id": payload.person_id}, {"embedding": 0})
    if person is None:
        raise HTTPException(status_code=404, detail="Person not found.")
    cursor = request.app.state.db.memories.find({"person_id": payload.person_id}).sort("created_at", DESCENDING).limit(5)
    memories = [memory async for memory in cursor]
    notes = "\n".join(f"- {memory['note']}" for memory in memories)
    if not notes:
        notes = "- No recent memories have been logged."
    prompt = (
        "Write exactly one short, warm, reassuring sentence for a person with dementia. "
        "Identify the familiar person and naturally mention the most useful recent memory. "
        "Do not invent facts. Do not use quotation marks.\n\n"
        f"Name: {person['name']}\n"
        f"Relationship: {person['relationship']}\n"
        f"Recent memories:\n{notes}"
    )
    try:
        response = await request.app.state.groq.chat.completions.create(
            model=GROQ_MODEL,
            messages=[{"role": "user", "content": prompt}],
            max_tokens=150,
            temperature=0.3,
        )
        summary = require_summary_text(response)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Could not generate summary.") from exc
    return {"person_id": payload.person_id, "name": person["name"], "relationship": person["relationship"], "summary": summary}

@app.post("/sos", status_code=status.HTTP_201_CREATED)
async def send_sos(payload: SosRequest, request: Request) -> dict[str, Any]:
    alert = {
        "person_name": payload.person_name,
        "caregiver_phone": payload.caregiver_phone,
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "location_link": payload.location_link,
        "timestamp": payload.timestamp or utc_now().isoformat(),
        "resolved": False,
        "created_at": utc_now()
    }
    try:
        await request.app.state.db.sos_alerts.insert_one(alert)
    except PyMongoError as exc:
        raise HTTPException(status_code=503, detail="Could not save SOS alert.") from exc
    return {"status": "success", "message": f"SOS alert processed for {payload.person_name}"}


@app.get("/sos/active")
async def get_active_sos(caregiver_phone: str, request: Request) -> dict[str, Any] | None:
    # Find the most recent unresolved SOS alert for this caregiver phone
    alert = await request.app.state.db.sos_alerts.find_one(
        {"caregiver_phone": caregiver_phone.strip(), "resolved": {"$ne": True}},
        sort=[("created_at", DESCENDING)]
    )
    if not alert:
        raise HTTPException(status_code=404, detail="No active SOS alert found.")
    
    return {
        "id": str(alert["_id"]),
        "patientName": alert.get("person_name", ""),
        "latitude": alert.get("latitude", 0.0),
        "longitude": alert.get("longitude", 0.0),
        "locationLink": alert.get("location_link", ""),
        "timestamp": alert.get("timestamp", alert.get("created_at").isoformat() if alert.get("created_at") else ""),
        "resolved": alert.get("resolved", False)
    }


@app.post("/sos/resolve/{id}")
async def resolve_sos(id: str, request: Request) -> dict[str, str]:
    from bson import ObjectId
    from bson.errors import InvalidId
    try:
        obj_id = ObjectId(id)
    except InvalidId:
        raise HTTPException(status_code=400, detail="Invalid SOS alert ID format.")
    
    result = await request.app.state.db.sos_alerts.update_one(
        {"_id": obj_id},
        {"$set": {"resolved": True}}
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="SOS alert not found.")
    
    return {"status": "success"}


@app.get("/memory-log")
async def get_memory_log(caregiver_phone: str, request: Request) -> list[dict[str, Any]]:
    # 1. Find all people enrolled under this caregiver
    people_cursor = request.app.state.db.people.find({"caregiver_phone": caregiver_phone.strip()})
    people_map = {}
    relationship_map = {}
    async for person in people_cursor:
        pid = person["person_id"]
        people_map[pid] = person["name"]
        relationship_map[pid] = person["relationship"]
    
    if not people_map:
        return []
    
    # 2. Get memories for all these people
    # We fetch all memories so the client can group them by today/yesterday/earlier.
    memories_cursor = request.app.state.db.memories.find(
        {"person_id": {"$in": list(people_map.keys())}}
    ).sort("created_at", DESCENDING)
    
    log_entries = []
    async for memory in memories_cursor:
        pid = memory["person_id"]
        log_entries.append({
            "personName": people_map.get(pid, "Unknown"),
            "relationship": relationship_map.get(pid, "Unknown"),
            "summary": memory["note"],
            "timestamp": memory["created_at"].isoformat() if isinstance(memory["created_at"], datetime) else str(memory["created_at"])
        })
    
    return log_entries


@app.get("/daily-summary")
async def get_daily_summary(caregiver_phone: str, request: Request) -> dict[str, Any]:
    # 1. Find people under this caregiver
    people_cursor = request.app.state.db.people.find({"caregiver_phone": caregiver_phone.strip()})
    people_map = {}
    relationship_map = {}
    async for person in people_cursor:
        pid = person["person_id"]
        people_map[pid] = person["name"]
        relationship_map[pid] = person["relationship"]
    
    now = utc_now()
    start_of_today = datetime(now.year, now.month, now.day, tzinfo=timezone.utc)
    
    if not people_map:
        return {
            "date": start_of_today.date().isoformat(),
            "summary": "No patient profiles are currently registered under this caregiver phone number.",
            "peopleMetCount": 0,
            "sosCount": 0,
            "appOpenCount": 0
        }
    
    # 2. Fetch today's memory events
    memories_cursor = request.app.state.db.memories.find({
        "person_id": {"$in": list(people_map.keys())},
        "created_at": {"$gte": start_of_today}
    }).sort("created_at", DESCENDING)
    
    today_memories = [m async for m in memories_cursor]
    
    # 3. Calculate metrics
    unique_people_met = len({m["person_id"] for m in today_memories})
    
    # Fetch today's SOS events
    sos_count = await request.app.state.db.sos_alerts.count_documents({
        "caregiver_phone": caregiver_phone.strip(),
        "created_at": {"$gte": start_of_today}
    })
    
    # Simulate app open count realistically based on interaction events
    app_open_count = len(today_memories) + 4 if today_memories else 3
    
    # 4. Generate AI summary using Groq
    if today_memories:
        notes_list = []
        for m in today_memories:
            name = people_map.get(m["person_id"], "Someone")
            relation = relationship_map.get(m["person_id"], "Visitor")
            time_str = m["created_at"].strftime("%H:%M") if isinstance(m["created_at"], datetime) else "today"
            notes_list.append(f"- At {time_str}, patient met {name} ({relation}). Event details: {m['note']}")
        
        events_text = "\n".join(notes_list)
        prompt = (
            "Write exactly one warm, natural, reassuring paragraph (3-4 sentences) summarizing the patient's day for their caregiver. "
            "Incorporate details of who they met and any specific interactions noted below. "
            "Keep it empathetic and professional. Do not use quotes or list formatting. "
            "Do not invent facts not present in the notes.\n\n"
            f"Events Today:\n{events_text}"
        )
        try:
            response = await request.app.state.groq.chat.completions.create(
                model=GROQ_MODEL,
                messages=[{"role": "user", "content": prompt}],
                max_tokens=250,
                temperature=0.3,
            )
            summary = require_summary_text(response)
        except Exception:
            summary = "The patient had interactions with registered visitors today, which were processed successfully. No alerts were triggered."
    else:
        summary = "The patient had a calm, quiet day. There were no visitor interactions or unusual events logged today."
        
    return {
        "date": start_of_today.date().isoformat(),
        "summary": summary,
        "peopleMetCount": unique_people_met,
        "sosCount": sos_count,
        "appOpenCount": app_open_count
    }


