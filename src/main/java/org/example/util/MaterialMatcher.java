package org.example.util;

import org.example.entity.MaterialMaster;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物料名称匹配与候选排序 —— 纯函数，不依赖数据库，便于完整单测。
 *
 * <p>单独抽出来的理由与 {@link ImportDedupeUtil} 相同：这里是本功能<b>唯一有认知陷阱</b>的地方
 * （子串歧义、一名多码、型号混淆），把规则钉在可单测的纯函数里，比埋在 Service 里靠联调发现要可靠。
 *
 * <h3>为什么自动匹配不做模糊</h3>
 * 主数据里存在 <b>271 对「一个名称是另一个名称的子串」</b>的情况
 * （「电石」⊂「电石渣」、「HND-2171」⊂「HND-2171前馏份」…）。
 * 若用包含式模糊匹配，「电石」会命中「电石渣」，产出一个<b>格式合法但错误</b>的编码。
 * 因此自动匹配只认「归一化后全等」且必须唯一命中；其余情况一律交人工。
 *
 * <h3>检索（人工挑物料）的分档</h3>
 * <ol>
 *   <li><b>全等</b>：归一化后完全相同</li>
 *   <li><b>包含</b>：名称/编码与关键词互为子串，<b>忽略分隔符写法差异</b>
 *       （用户写「HND-V171 200」，主数据写「HND-V171_200kg_…」）</li>
 *   <li><b>型号对齐</b>：关键词里的<b>字母数字段</b>（HND / V150 / 200）与汉字都原样出现</li>
 *   <li><b>共同字符</b>：<b>仅在第 1–3 档一个结果都没有时才返回</b>，用于手写误读时给人工留线索</li>
 * </ol>
 *
 * <p>第 3 档是「搜 V150 不能出现 D150」的落点：<b>型号是标识符，不是模糊文本</b> ——
 * V150 / D150 / V2171 / D2171 只差一两个字符，按字符相似度排序必然混在一起，
 * 所以要求字母数字段<b>原样</b>出现，对不上就不进第 3 档。
 * 第 4 档做成「兜底而非排在最后」，是因为用户明确要求「结果不能有其他的产品」——
 * 排最后仍会出现，排除掉才不会。
 */
public final class MaterialMatcher {

    /** 相关度分档，数字越小越靠前 */
    public static final int TIER_EXACT = 0;          // 归一化后全等
    public static final int TIER_SUBSTRING = 1;      // 名称/编码与关键词互为子串（忽略分隔符写法）
    public static final int TIER_TOKENS = 2;         // 字母数字段与汉字都原样出现
    public static final int TIER_SHARED_CHARS = 3;   // 兜底档：仅在上面几档全空时才返回
    public static final int TIER_NONE = 4;           // 不相关，不返回

    /** 字母数字段：型号、编号、规格数字都靠它识别 */
    private static final Pattern ALNUM = Pattern.compile("[a-z0-9]+");

    private MaterialMatcher() {
    }

    /**
     * 严格匹配：归一化后全等。
     *
     * <p>返回<b>全部</b>命中项而不是第一个 —— 调用方要据此判断是否唯一。
     * 结果多于一条说明该名称确实对应多个物料，必须交人工，不能随便取一条。
     */
    public static List<MaterialMaster> findExact(List<MaterialMaster> candidates, String keyword) {
        List<MaterialMaster> hits = new ArrayList<>();
        if (candidates == null || keyword == null || keyword.isEmpty()) {
            return hits;
        }
        for (MaterialMaster material : candidates) {
            if (keyword.equals(MaterialNameNormalizer.normalize(material.getMaterialName()))) {
                hits.add(material);
            }
        }
        return hits;
    }

    /**
     * 按相关度取候选。
     *
     * <p>同档内先按「名称长度与关键词长度之差」升序（越接近越可能是目标），再按编码升序，
     * 保证同一关键词每次返回顺序稳定。
     *
     * @param searchSpec 规格是否参与匹配。自动匹配时<b>不参与</b>：规格多是「200kg铁桶」
     *                   这类包装信息，会让不同物料挤进候选；人工检索时开启，便于按规格回忆物料。
     */
    public static List<MaterialMaster> rank(List<MaterialMaster> candidates, String keyword,
                                            int limit, boolean searchSpec) {
        if (candidates == null || keyword == null || keyword.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }

        List<String> runs = alnumRuns(keyword);
        List<String> han = hanChars(keyword);

        List<Scored> strict = new ArrayList<>();
        List<Scored> loose = new ArrayList<>();

        for (MaterialMaster material : candidates) {
            String name = MaterialNameNormalizer.normalize(material.getMaterialName());
            String code = MaterialNameNormalizer.normalize(material.getMaterialCode());
            String spec = searchSpec ? MaterialNameNormalizer.normalize(material.getSpec()) : null;

            int tier = tierOf(name, code, spec, keyword, runs, han);
            if (tier == TIER_NONE) {
                continue;
            }

            int shared = commonChars(name, keyword);
            if (spec != null) {
                shared = Math.max(shared, commonChars(spec, keyword));
            }

            Scored scored = new Scored(material, tier, Math.abs(name.length() - keyword.length()), shared);
            if (tier == TIER_SHARED_CHARS) {
                loose.add(scored);
            } else {
                strict.add(scored);
            }
        }

        // 有明确结果时只给明确的 —— 「搜 V150 不能出现 D150」；
        // 一个都没有时才退到共同字符档，给人工留线索（手写误读的场景靠它）
        List<Scored> chosen = strict.isEmpty() ? loose : strict;
        chosen.sort((a, b) -> {
            int cmp = Integer.compare(a.tier(), b.tier());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(b.sharedChars(), a.sharedChars());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(a.lengthDiff(), b.lengthDiff());
            if (cmp != 0) {
                return cmp;
            }
            return String.valueOf(a.material().getMaterialCode())
                    .compareTo(String.valueOf(b.material().getMaterialCode()));
        });

        List<MaterialMaster> result = new ArrayList<>();
        for (Scored scored : chosen) {
            if (result.size() >= limit) {
                break;
            }
            result.add(scored.material());
        }
        return result;
    }

    private static int tierOf(String name, String code, String spec, String keyword,
                              List<String> runs, List<String> han) {
        if (keyword.equals(name) || keyword.equals(code)) {
            return TIER_EXACT;
        }

        if (name.contains(keyword) || keyword.contains(name) || code.contains(keyword)) {
            return TIER_SUBSTRING;
        }
        // 分隔符写法不一致时的包含关系：用户写「HND-V171 200」，主数据写「HND-V171_200kg_…」。
        // 不压掉分隔符就永远对不上，只能掉到模糊档。
        String squashedKeyword = squash(keyword);
        if (!squashedKeyword.isEmpty()
                && (squash(name).contains(squashedKeyword) || squashedKeyword.contains(squash(name))
                    || squash(code).contains(squashedKeyword))) {
            return TIER_SUBSTRING;
        }

        if (!runs.isEmpty()) {
            // 有型号/编号时，它们必须<b>原样</b>出现 —— 这是 V150 与 D150 的分水岭
            if (containsTokens(name, runs, han)
                    || containsTokens(spec, runs, han)
                    || containsTokens(code, runs, han)) {
                return TIER_TOKENS;
            }
            return hasCommonChar(name, spec, keyword) ? TIER_SHARED_CHARS : TIER_NONE;
        }

        // 纯中文关键词：没有可对齐的型号，只能按共同字符给线索
        return hasCommonChar(name, spec, keyword) ? TIER_SHARED_CHARS : TIER_NONE;
    }

    private static boolean hasCommonChar(String name, String spec, String keyword) {
        return commonChars(name, keyword) > 0 || commonChars(spec, keyword) > 0;
    }

    /**
     * hay 里是否同时出现了<b>全部字母数字段</b>与<b>全部汉字</b>。
     *
     * <p>字母数字段按「压掉分隔符后包含」判断（`v150200` 应能对上 `hnd-v150_200kg`），
     * 汉字则逐字判断。
     */
    private static boolean containsTokens(String hay, List<String> runs, List<String> han) {
        if (hay == null || hay.isEmpty()) {
            return false;
        }
        String squashed = squash(hay);
        for (String run : runs) {
            if (!squashed.contains(run)) {
                return false;
            }
        }
        for (String ch : han) {
            if (hay.indexOf(ch) < 0) {
                return false;
            }
        }
        return true;
    }

    /** 归一化后的字母数字段（型号 / 编号 / 规格数字） */
    private static List<String> alnumRuns(String normalized) {
        List<String> runs = new ArrayList<>();
        Matcher matcher = ALNUM.matcher(normalized);
        while (matcher.find()) {
            runs.add(matcher.group());
        }
        return runs;
    }

    /** 归一化后的汉字（逐个） */
    private static List<String> hanChars(String normalized) {
        List<String> chars = new ArrayList<>();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chars.add(String.valueOf(c));
            }
        }
        return chars;
    }

    /**
     * 共同字符数（按出现次数配对，不重复计数）。仅用于兜底档排序 ——
     * 手写误读时（如把「三氯氢硅」读成「三砖」）要求全字符出现会一个候选都给不出，
     * 人工就无从下手。
     */
    private static int commonChars(String target, String keyword) {
        if (target == null || target.isEmpty() || keyword == null || keyword.isEmpty()) {
            return 0;
        }
        boolean[] used = new boolean[target.length()];
        int shared = 0;
        for (int i = 0; i < keyword.length(); i++) {
            for (int j = 0; j < target.length(); j++) {
                if (!used[j] && target.charAt(j) == keyword.charAt(i)) {
                    used[j] = true;
                    shared++;
                    break;
                }
            }
        }
        return shared;
    }

    /**
     * 在归一化之上再压掉分隔符（空白、下划线、连字符、点）。
     *
     * <p>只用于「包含关系」的判断，<b>不参与全等判定</b> ——
     * 全等要留给 {@link #findExact} 那条保守路径，压分隔符会让不同型号值撞在一起。
     */
    private static String squash(String normalized) {
        return normalized == null ? "" : normalized.replaceAll("[\\s_\\-.]", "");
    }

    /** 排序用的中间结果 */
    private record Scored(MaterialMaster material, int tier, int lengthDiff, int sharedChars) {
    }
}
