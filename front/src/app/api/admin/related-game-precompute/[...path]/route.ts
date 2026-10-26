import { NextRequest } from "next/server";
import { relayAuthHeaders } from "@/lib/proxy";

type Context = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: Context) {
  const { path } = await context.params;
  const isRuns = path.length === 1 && path[0] === "runs";
  const isChanges = path[0] === "changes" && (path.length === 1 || (path.length === 2 && ["targets", "preview"].includes(path[1])));
  const isStatus = path.length === 1 && path[0] === "status";
  const isDetail = path[0] === "runs" && /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(path[1] || "")
    && (path.length === 2 || (path.length === 3 && path[2] === "failures"));
  if (!isRuns && !isStatus && !isDetail && !isChanges) {
    return Response.json({ msg: "없는 추천 갱신 API입니다." }, { status: 404 });
  }
  if (request.method === "POST" && !isRuns) {
    return Response.json({ msg: "허용하지 않는 요청입니다." }, { status: 405, headers: { Allow: "GET" } });
  }

  const headers = new Headers();
  for (const name of ["authorization", "cookie", "content-type"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  try {
    const url = new URL(`/api/v1/admin/related-game-precompute/${path.join("/")}`, process.env.BACKEND_URL || "http://localhost:8080");
    for (const key of ["page", "size"]) {
      const value = request.nextUrl.searchParams.get(key);
      if (value !== null) url.searchParams.set(key, value);
    }
    const upstream = await fetch(url, {
      method: request.method,
      headers,
      body: request.method === "POST" ? await request.text() : undefined,
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
      redirect: "manual",
    });
    const responseHeaders = new Headers({ "Content-Type": "application/json", "Cache-Control": "no-store" });
    relayAuthHeaders(upstream, responseHeaders);
    return new Response(await upstream.text(), { status: upstream.status, headers: responseHeaders });
  } catch {
    return Response.json(
      { msg: "서버 응답을 확인하지 못했습니다. 실행 요청은 접수되었을 수 있으니 상태와 실행 이력을 확인하세요." },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}

export const GET = proxy;
export const POST = proxy;
