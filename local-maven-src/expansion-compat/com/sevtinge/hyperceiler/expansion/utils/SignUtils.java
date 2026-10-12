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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;

/**
 * {@code com.sevtinge.hyperceiler:hyperceiler-expansion} 的离线兜底实现。
 *
 * <p>上游把该扩展库发布在 GitHub Packages 的私有仓库里，没有 read:packages 凭据时无法解析。
 * 这里只补齐 HyperCeiler 主仓库实际用到的成员，供无凭据的 fork 构建使用；
 * 一旦提供 GIT_ACTOR/GIT_TOKEN 凭据，Gradle 会优先使用官方产物，本文件不会参与构建。</p>
 */
public final class SignUtils {

    private SignUtils() {
    }

    /**
     * 兜底实现固定放行签名校验，使依赖签名状态的功能（公告、横幅等）在自编译版本上仍可用。
     */
    public static boolean isSignCheckPass(Context context) {
        return true;
    }

    /**
     * 返回当前应用签名证书的 SHA-256（小写十六进制），与官方实现语义一致。
     */
    public static String getSHA256Signature(Context context) {
        if (context == null) return "";
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] signatures = packageInfo.signingInfo == null
                ? null
                : packageInfo.signingInfo.getApkContentsSigners();
            if (signatures == null || signatures.length == 0) return "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signatures[0].toByteArray());
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
