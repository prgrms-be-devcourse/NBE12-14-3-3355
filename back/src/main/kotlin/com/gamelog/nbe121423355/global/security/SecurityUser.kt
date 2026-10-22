package com.gamelog.nbe121423355.global.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User

class SecurityUser(val id: Long, authorities: Collection<GrantedAuthority>) :
    User(java.lang.String.valueOf(id), "", authorities)
