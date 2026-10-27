"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef } from "react";
import { useAuth } from "@/features/auth/auth-context";
import { ReviewApiError } from "@/features/reviews/api";

// 401이면 토큰을 한 번 재발급해 다시 요청하고, 그래도 실패하면 로그인 화면으로 보낸다.
export function useAuthorized(loginPath: string) {
  const { accessToken, refresh, logout } = useAuth();
  const router = useRouter();
  const tokenRef = useRef(accessToken);
  useEffect(() => { tokenRef.current = accessToken; }, [accessToken]);

  return useCallback(async <T,>(operation: (token: string) => Promise<T>): Promise<T> => {
    try {
      return await operation(tokenRef.current!);
    } catch (reason) {
      if (!(reason instanceof ReviewApiError) || reason.status !== 401) throw reason;
      const token = await refresh();
      if (!token) {
        router.replace(loginPath);
        throw reason;
      }
      tokenRef.current = token;
      try {
        return await operation(token);
      } catch (retryError) {
        if (retryError instanceof ReviewApiError && retryError.status === 401) {
          await logout();
          router.replace(loginPath);
        }
        throw retryError;
      }
    }
  }, [refresh, logout, router, loginPath]);
}

export type Authorized = ReturnType<typeof useAuthorized>;
