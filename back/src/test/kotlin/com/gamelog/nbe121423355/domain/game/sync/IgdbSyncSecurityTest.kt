package com.gamelog.nbe121423355.domain.game.sync

import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
class IgdbSyncSecurityTest {
    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun anonymousCannotReadOrStart() {
        mvc.perform(get("/api/v1/admin/igdb-sync")).andExpect(status().isUnauthorized())
        mvc.perform(post("/api/v1/admin/igdb-sync")).andExpect(status().isUnauthorized())
    }

    @Test
    fun ordinaryUsersCannotReadOrStart() {
        mvc.perform(get("/api/v1/admin/igdb-sync").with(user("user").roles("USER")))
            .andExpect(status().isForbidden())
        mvc.perform(post("/api/v1/admin/igdb-sync").with(user("user").roles("USER")))
            .andExpect(status().isForbidden())
    }

    @Test
    fun administratorCanReadStatus() {
        mvc.perform(get("/api/v1/admin/igdb-sync").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IDLE"))
    }
}
