package com.gamelog.nbe121423355.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

// @Scheduled 작업 활성화 (만료된 RefreshToken 및 보관 기간이 지난 알림 정리)
// 단일 인스턴스 기준. 서버가 여러 대가 되면 중복 실행 방지(ShedLock 등) 필요
@Configuration @EnableScheduling class SchedulingConfig
