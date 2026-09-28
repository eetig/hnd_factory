package org.example.util;

/**
 * 物料名称归一化 —— <b>全项目唯一实现</b>（匹配与检索都走这里，不再有第二份）。
 *
 * <p>只做「不影响语义」的归一：去空白（含全角空格）、全角转半角、转小写。
 *
 * <p><b>刻意不做的</b>：不去括号、不去连字符、不删下划线、不做分词、不做同义替换。
 * 原因是主数据里存在 <b>271 对「一个名称是另一个名称的子串」</b>的情况
 * （如「电石」⊂「电石渣」、「HND-2171」⊂「HND-2171前馏份」），
 * 任何放宽边界的归一都可能把两个不同物料压成同一个 key，进而错配编码 ——
 * 而错配的编码格式合法，能一路混到落库，比查不到危险得多。
 *
 * <p>实测：本归一化不引入新的碰撞（主数据 244 条中「一名多码」的组数保持为 6，
 * 与未归一化时一致）。
 */
public final class MaterialNameNormalizer {

    private MaterialNameNormalizer() {
    }

    /**
     * 归一化物料名称；入参为 null 时返回空串（调用方据此判空即可）。
     */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            // 全角 ASCII（U+FF01..U+FF5E）→ 半角（U+0021..U+007E）
            // 只转 ASCII 区，全角空格 U+3000 与全角汉字不受影响
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            }

            // 半角空格、全角空格 U+3000 均在此被剔除
            if (Character.isWhitespace(c)) {
                continue;
            }

            // 中文不受影响；仅让英文/编码大小写不敏感
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
