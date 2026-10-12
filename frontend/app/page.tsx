"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Webcam, { WebcamHandle } from "@/components/Webcam";
import {
  identifyFace,
  PersonMatch,
  summarizePerson,
  logMemory,
} from "@/lib/api";

type Recognition = PersonMatch & {
  confidence: number;
  summary: string;
};

type Challenge = "TURN LEFT" | "TURN RIGHT" | "SMILE";
type LivenessStatus = "waiting" | "passed" | "failed";

const CHALLENGES: Challenge[] = ["TURN LEFT", "TURN RIGHT", "SMILE"];

function pickChallenge(): Challenge {
  return CHALLENGES[Math.floor(Math.random() * CHALLENGES.length)];
}

export default function Home() {
  const webcamRef = useRef<WebcamHandle>(null);
  const requestInFlight = useRef(false);
  const summaryCache = useRef<{ personId: string; summary: string } | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const faceMeshRef = useRef<any>(null);
  const animFrameRef = useRef<number | null>(null);
  const challengeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const liveVerified = useRef(false);
  const baseNoseX = useRef<number | null>(null);

  const [cameraReady, setCameraReady] = useState(false);
  const [recognition, setRecognition] = useState<Recognition | null>(null);
  const [status, setStatus] = useState("Looking for a familiar face...");
  const [challenge, setChallenge] = useState<Challenge>(pickChallenge);
  const challengeRef = useRef<Challenge>(challenge);
  challengeRef.current = challenge;
  const [livenessStatus, setLivenessStatus] = useState<LivenessStatus>("waiting");
  const [faceMeshReady, setFaceMeshReady] = useState(false);

  // Memory modal state
  const [showMemoryModal, setShowMemoryModal] = useState(false);
  const [memoryNote, setMemoryNote] = useState("");
  const [memorySaving, setMemorySaving] = useState(false);
  const [memorySaved, setMemorySaved] = useState(false);

  // Load MediaPipe FaceMesh from CDN
  useEffect(() => {
    const script1 = document.createElement("script");
    script1.src = "https://cdn.jsdelivr.net/npm/@mediapipe/camera_utils/camera_utils.js";
    script1.crossOrigin = "anonymous";
    document.head.appendChild(script1);

    const script2 = document.createElement("script");
    script2.src = "https://cdn.jsdelivr.net/npm/@mediapipe/face_mesh/face_mesh.js";
    script2.crossOrigin = "anonymous";
    script2.onload = () => {
      const fm = new (window as any).FaceMesh({
        locateFile: (file: string) =>
          `https://cdn.jsdelivr.net/npm/@mediapipe/face_mesh/${file}`,
      });
      fm.setOptions({
        maxNumFaces: 1,
        refineLandmarks: true,
        minDetectionConfidence: 0.5,
        minTrackingConfidence: 0.5,
      });
      fm.onResults((results: any) => {
        if (!results.multiFaceLandmarks || results.multiFaceLandmarks.length === 0) return;
        const lm = results.multiFaceLandmarks[0];
        if (liveVerified.current) return;

        const currentChallenge = challengeRef.current;

        if (currentChallenge === "TURN LEFT" || currentChallenge === "TURN RIGHT") {
          const noseX = lm[1].x;
          if (baseNoseX.current === null) {
            baseNoseX.current = noseX;
            return;
          }
          const delta = noseX - baseNoseX.current;
          if (currentChallenge === "TURN LEFT" && delta > 0.05) {
            passLiveness();
          } else if (currentChallenge === "TURN RIGHT" && delta < -0.05) {
            passLiveness();
          }
        } else if (currentChallenge === "SMILE") {
          const leftMouth = lm[61];
          const rightMouth = lm[291];
          const leftCheek = lm[234];
          const rightCheek = lm[454];
          const mouthWidth = Math.abs(rightMouth.x - leftMouth.x);
          const faceWidth = Math.abs(rightCheek.x - leftCheek.x);
          const smileRatio = mouthWidth / faceWidth;
          if (smileRatio > 0.45) {
            passLiveness();
          }
        }
      });
      faceMeshRef.current = fm;
      setFaceMeshReady(true);
    };
    document.head.appendChild(script2);

    return () => {
      document.head.removeChild(script1);
      document.head.removeChild(script2);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const passLiveness = useCallback(() => {
    liveVerified.current = true;
    setLivenessStatus("passed");
    if (challengeTimeoutRef.current) clearTimeout(challengeTimeoutRef.current);
    setTimeout(() => {
      liveVerified.current = false;
      baseNoseX.current = null;
      setLivenessStatus("waiting");
      setChallenge(pickChallenge());
    }, 30000);
  }, []);

  const failLiveness = useCallback(() => {
    liveVerified.current = false;
    baseNoseX.current = null;
    setLivenessStatus("failed");
    setTimeout(() => {
      setLivenessStatus("waiting");
      setChallenge(pickChallenge());
    }, 2000);
  }, []);

  useEffect(() => {
    if (!faceMeshReady || !cameraReady) return;
    const video = document.querySelector("video") as HTMLVideoElement | null;
    if (!video) return;
    videoRef.current = video;

    let running = true;
    const detect = async () => {
      if (!running || !faceMeshRef.current || !videoRef.current) return;
      if (videoRef.current.readyState >= 2) {
        await faceMeshRef.current.send({ image: videoRef.current });
      }
      animFrameRef.current = requestAnimationFrame(detect);
    };
    animFrameRef.current = requestAnimationFrame(detect);

    challengeTimeoutRef.current = setTimeout(() => {
      if (!liveVerified.current) failLiveness();
    }, 10000);

    return () => {
      running = false;
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current);
      if (challengeTimeoutRef.current) clearTimeout(challengeTimeoutRef.current);
    };
  }, [faceMeshReady, cameraReady, failLiveness]);

  const scan = useCallback(async () => {
    if (requestInFlight.current) return;
    if (!liveVerified.current) return;
    const image = webcamRef.current?.capture();
    if (!image) return;

    requestInFlight.current = true;
    setStatus("Checking this face...");
    try {
      const identified = await identifyFace(image);
      if (identified.match) {
        let summary = summaryCache.current?.summary;
        if (summaryCache.current?.personId !== identified.match.person_id) {
          const response = await summarizePerson(identified.match.person_id);
          summary = response.summary;
          summaryCache.current = {
            personId: identified.match.person_id,
            summary,
          };
        }
        setRecognition({
          ...identified.match,
          confidence: identified.confidence,
          summary: summary ?? "",
        });
        setStatus("Familiar face recognized");
      } else {
        setRecognition(null);
        setStatus("Looking for a familiar face...");
      }
    } catch (error) {
      setRecognition(null);
      setStatus(error instanceof Error ? error.message : "Unable to identify face.");
    } finally {
      requestInFlight.current = false;
    }
  }, []);

  useEffect(() => {
    if (!cameraReady) return;
    void scan();
    const interval = window.setInterval(() => void scan(), 3000);
    return () => window.clearInterval(interval);
  }, [cameraReady, scan]);

  const handleSaveMemory = async () => {
    if (!recognition || !memoryNote.trim()) return;
    setMemorySaving(true);
    try {
      await logMemory(recognition.person_id, memoryNote.trim());
      summaryCache.current = null;
      setMemorySaved(true);
      setMemoryNote("");
      setTimeout(() => {
        setMemorySaved(false);
        setShowMemoryModal(false);
      }, 1500);
    } catch (e) {
      console.error(e);
    } finally {
      setMemorySaving(false);
    }
  };

  const pillColor =
    livenessStatus === "passed"
      ? "bg-green-500/20 text-green-400 border-green-500/30"
      : livenessStatus === "failed"
      ? "bg-red-500/20 text-red-400 border-red-500/30"
      : "bg-yellow-500/20 text-yellow-400 border-yellow-500/30";

  const pillText =
    livenessStatus === "passed"
      ? "✓ Live verified"
      : livenessStatus === "failed"
      ? "✗ No live face detected"
      : `👁 ${challenge}`;

  return (
    <main className="mx-auto flex min-h-screen max-w-6xl flex-col px-5 pb-10 pt-24">
      <div className="mb-6">
        <p className="mb-2 text-sm font-medium uppercase tracking-[0.22em] text-mint">
          Your memory companion
        </p>
        <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">
          See someone you know.
        </h1>
      </div>

      <section className="relative min-h-[540px] flex-1 overflow-hidden rounded-[2rem] border border-white/10 bg-panel shadow-glow">
        <Webcam
          ref={webcamRef}
          className="absolute inset-0"
          onReadyChange={setCameraReady}
        />
        <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/20" />

        {/* Status pill - top left */}
        <div className="absolute left-5 top-5 rounded-full border border-white/15 bg-black/45 px-4 py-2 text-sm text-slate-200 backdrop-blur-md">
          <span className="mr-2 inline-block h-2 w-2 animate-pulse rounded-full bg-mint" />
          {status}
        </div>

        {/* Liveness challenge pill - top right */}
        <div className={`absolute right-5 top-5 rounded-full border px-4 py-2 text-sm font-medium backdrop-blur-md ${pillColor}`}>
          {pillText}
        </div>

        {recognition && (
  <article className="absolute inset-x-5 bottom-5 max-w-lg rounded-3xl border border-white/10 bg-slate-950/90 p-5 shadow-2xl backdrop-blur-xl sm:left-6 sm:bottom-6">
    <div className="flex items-center gap-4 mb-3">
      {/* Avatar initial */}
      <div className="flex-shrink-0 h-12 w-12 rounded-2xl bg-mint/15 border border-mint/20 flex items-center justify-center">
        <span className="text-lg font-bold text-mint">
          {recognition.name.charAt(0).toUpperCase()}
        </span>
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-semibold uppercase tracking-widest text-mint mb-0.5">
          {recognition.relationship}
        </p>
        <h2 className="text-2xl font-semibold leading-tight capitalize">
          {recognition.name}
        </h2>
      </div>
      <span className="flex-shrink-0 rounded-full bg-mint/10 px-3 py-1 text-xs font-medium text-mint border border-mint/20">
        {Math.round(recognition.confidence * 100)}% match
      </span>
    </div>

    {/* Divider */}
    <div className="border-t border-white/8 my-3" />

    <p className="text-sm leading-relaxed text-slate-300">
      {recognition.summary}
    </p>

    <button
      onClick={() => setShowMemoryModal(true)}
      className="mt-3 flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-1.5 text-xs text-slate-400 transition hover:bg-white/10 hover:text-slate-200"
    >
      <span>＋</span> Add a memory
    </button>
  </article>
)}
      </section>

      {/* Memory modal */}
      {showMemoryModal && recognition && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-white/15 bg-slate-950 p-8 shadow-2xl">
            <h3 className="mb-1 text-xl font-semibold">Add a memory</h3>
            <p className="mb-5 text-sm text-slate-400">
              Write a note about {recognition.name} — it will make future summaries more personal.
            </p>
            <textarea
              className="w-full rounded-2xl border border-white/10 bg-white/5 p-4 text-sm text-slate-200 placeholder-slate-500 outline-none focus:border-mint/50 focus:ring-0"
              rows={4}
              placeholder={`e.g. "${recognition.name} visited today, we watched the game and had tea."`}
              value={memoryNote}
              onChange={(e) => setMemoryNote(e.target.value)}
              autoFocus
            />
            <div className="mt-4 flex gap-3">
              <button
                onClick={handleSaveMemory}
                disabled={memorySaving || !memoryNote.trim()}
                className="flex-1 rounded-2xl bg-mint px-5 py-3 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {memorySaved ? "✓ Saved!" : memorySaving ? "Saving..." : "Save memory"}
              </button>
              <button
                onClick={() => { setShowMemoryModal(false); setMemoryNote(""); }}
                className="rounded-2xl border border-white/10 px-5 py-3 text-sm text-slate-400 transition hover:bg-white/5"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}