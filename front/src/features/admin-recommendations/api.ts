import { acceptRefreshedToken } from "@/features/auth/api";

export type TargetType = "ALL" | "IDS" | "FAILED" | "UNREADY";
export type RunStatus = "ACCEPTED" | "PREPARING" | "RUNNING" | "SUCCEEDED" | "PARTIAL_FAILED" | "FAILED" | "INTERRUPTED" | "REJECTED";
export type Run = {
  runId: string;
  requestedBy: number;
  type: TargetType;
  gameIds: number[];
  sourceRunId: string | null;
  status: RunStatus;
  upperGameId: number | null;
  estimatedTotal: number | null;
  processed: number;
  succeeded: number;
  failed: number;
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  progressUpdatedAt: string | null;
  heartbeatAt: string;
  heartbeatExpired: boolean;
  elapsedSeconds: number;
  error: string | null;
};
export type Failure = { gameId: number; error: string; occurredAt: string };
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type ExecutionStatus = { databaseLocked: boolean; localRequestActive: boolean; activeRuns: Run[]; recoveryDeferred: boolean };
export type StartRequest = { type: TargetType; gameIds?: string; sourceRunId?: string };

export const targetLabels: Record<TargetType, string> = {
  ALL: "전체 갱신", IDS: "지정 ID 갱신", FAILED: "실패 게임 재처리", UNREADY: "최초 계산 미완료 게임 갱신",
};
export const statusLabels: Record<RunStatus, string> = {
  ACCEPTED: "접수됨", PREPARING: "계산 준비 중", RUNNING: "계산 중", SUCCEEDED: "완료",
  PARTIAL_FAILED: "완료 · 일부 게임 실패", FAILED: "작업 실패", INTERRUPTED: "중단됨", REJECTED: "실행 거절",
};
export function isActive(run: Run) {
  return ["ACCEPTED", "PREPARING", "RUNNING"].includes(run.status);
}

// Long 범위의 ID는 숫자로 변환하지 않고 문자열로 검증해 입력 정밀도를 유지한다.
export function normalizeGameIds(text: string): string {
  if (text.length > 30000) throw new Error("게임 ID 입력은 30,000자 이하여야 합니다.");
  const parts = text.trim().split(/[,\s]+/).filter(Boolean);
  if (!parts.length) throw new Error("갱신할 기준 게임 ID를 입력하세요.");
  const normalized = parts.map((part) => {
    if (!/^\d+$/.test(part)) throw new Error("게임 ID는 양의 정수만 입력하세요.");
    const value = part.replace(/^0+/, "");
    if (!value || value.length > 19 || (value.length === 19 && value > "9223372036854775807")) {
      throw new Error("게임 ID는 1부터 9223372036854775807 사이여야 합니다.");
    }
    return value;
  });
  const ids = [...new Set(normalized)].sort((a, b) => a.length - b.length || a.localeCompare(b));
  if (ids.length > 1000) throw new Error("중복을 제외한 게임 ID는 최대 1,000개까지 입력할 수 있습니다.");
  return ids.join(",");
}

export async function request<T>(token: string, path: string, options: { signal?: AbortSignal; body?: StartRequest } = {}): Promise<T> {
  const response = await fetch(`/api/admin/related-game-precompute/${path}`, {
    method: options.body ? "POST" : "GET",
    headers: { Authorization: `Bearer ${token}`, ...(options.body ? { "Content-Type": "application/json" } : {}) },
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: options.signal ?? AbortSignal.timeout(15000),
    cache: "no-store",
  });
  acceptRefreshedToken(response);
  const body = await response.json();
  if (!response.ok) throw new Error(body.msg || `요청에 실패했습니다. (${response.status})`);
  return body.data;
}
