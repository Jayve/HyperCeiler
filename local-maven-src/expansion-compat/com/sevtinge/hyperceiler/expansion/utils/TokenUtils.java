/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */

package com.sevtinge.hyperceiler.expansion.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@code com.sevtinge.hyperceiler:hyperceiler-expansion} 的离线兜底实现，详见 {@link SignUtils}。
 *
 * <p>设备令牌仅用于"设备信息"展示，官方算法不公开，这里退化为设备信息摘要的稳定哈希。</p>
 */
public final class TokenUtils {

    private TokenUtils() {
    }

    public static String getDeviceToken(String modelName, String serial, String androidId, String cpuId) {
        String raw = nullToEmpty(modelName) + '|' + nullToEmpty(serial) + '|'
            + nullToEmpty(androidId) + '|' + nullToEmpty(cpuId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.substring(0, 32);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
