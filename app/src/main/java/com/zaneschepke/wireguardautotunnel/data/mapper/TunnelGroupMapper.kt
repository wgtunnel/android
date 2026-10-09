package com.zaneschepke.wireguardautotunnel.data.mapper

import com.zaneschepke.wireguardautotunnel.data.entity.TunnelGroup as Entity
import com.zaneschepke.wireguardautotunnel.domain.model.TunnelGroup as Domain

fun Entity.toDomain(): Domain =
    Domain(id = id, name = name, position = position, expanded = expanded)

fun Domain.toEntity(): Entity =
    Entity(id = id, name = name, position = position, expanded = expanded)
