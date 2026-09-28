package org.example.util;

import org.example.entity.MaterialMaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 匹配规则单测。
 *
 * <p>夹具取自真实主数据（244 条中节选），包括那几组<b>已知会造成错配</b>的样本 ——
 * 整个功能的成败就取决于这些样本上的行为。
 */
class MaterialMatcherTest {

    private static MaterialMaster material(String code, String name) {
        return material(code, name, null);
    }

    private static MaterialMaster material(String code, String name, String spec) {
        MaterialMaster m = new MaterialMaster();
        m.setMaterialCode(code);
        m.setMaterialName(name);
        m.setSpec(spec);
        return m;
    }

    /** 真实主数据里的一组样本，含两对「互为子串」的名称与一组「一名多码」 */
    private static List<MaterialMaster> fixture() {
        return List.of(
                material("111001786", "电石"),
                material("114001903", "电石渣"),
                material("111001787", "三氯氢硅"),
                material("114001785", "硅粉"),
                material("114001897", "HND-V150"),
                material("114001940", "HND-V150合成粗品"),
                material("114002486", "HND-2171"),
                material("114002501", "HND-2171前馏份"),
                material("114002501", "HND-2171过渡馏份"),
                // 同一名称对应 4 个编码 —— 源数据里真实存在的「一名多码」
                material("220000469", "1000ml外贸氣化塑料瓶_客供_外贸"),
                material("220000470", "1000ml外贸氣化塑料瓶_客供_外贸"),
                material("220000471", "1000ml外贸氣化塑料瓶_客供_外贸"),
                material("220000472", "1000ml外贸氣化塑料瓶_客供_外贸"),
                material("220000469", "1000ml氟化塑料瓶", "1000ml/瓶"),
                // 固定型号：只差一两个字符，是两种不同的产品
                material("115055980", "HND-V150_200kg_塑料桶_蓝色_华耐德"),
                material("115055993", "HND-D150_200KG_内衬PVF铁桶_蓝色_华耐德"),
                material("115055994", "HND-D2150_200KG_内衬PVF铁桶_蓝色_华耐德"),
                material("114001896", "HND-V171"),
                material("115055979", "HND-V171_200kg_塑料桶_蓝色_华耐德"),
                material("115055989", "HND-V171_200kg_内衬PVF铁桶_蓝色_华耐德"),
                material("115055992", "HND-D171_200kg_内衬PVF铁桶_蓝色_华耐德"));
    }


    private static String norm(String s) {
        return MaterialNameNormalizer.normalize(s);
    }

    // ------------------------------------------------------------------
    // 安全性质：这是本功能存在的理由
    // ------------------------------------------------------------------

    @Test
    @DisplayName("★ 查「电石」绝不能命中「电石渣」—— 模糊匹配会产出格式合法但错误的编码")
    void exactMatchDoesNotLeakToLongerNames() {
        List<MaterialMaster> hits = MaterialMatcher.findExact(fixture(), norm("电石"));

        assertEquals(1, hits.size(), "「电石」必须唯一命中");
        assertEquals("111001786", hits.get(0).getMaterialCode());
        assertFalse(hits.stream().anyMatch(m -> "114001903".equals(m.getMaterialCode())),
                "「电石渣」是不同的物料，不能被当成「电石」");
    }

    @Test
    @DisplayName("★ 查「HND-V150」不得命中「HND-V150合成粗品」")
    void exactMatchDoesNotLeakToPrefixedNames() {
        List<MaterialMaster> hits = MaterialMatcher.findExact(fixture(), norm("HND-V150"));

        assertEquals("114001897", hits.get(0).getMaterialCode());
        assertEquals(1, hits.size());
    }

    @Test
    @DisplayName("★ 一名多码：必须返回多条（调用方据此拒绝自动填），不能随便取一条")
    void oneNameManyCodesReturnsAll() {
        List<MaterialMaster> hits = MaterialMatcher.findExact(fixture(), norm("1000ml外贸氣化塑料瓶_客供_外贸"));

        assertEquals(4, hits.size(), "该名称对应 4 个编码，必须全部返回，否则会随机选中一个");
    }

    @Test
    @DisplayName("查不到就返回空 —— 由调用方留空交人工，不做任何猜测")
    void unmatchedReturnsEmpty() {
        assertTrue(MaterialMatcher.findExact(fixture(), norm("三砖")).isEmpty());
        assertTrue(MaterialMatcher.findExact(fixture(), norm("")).isEmpty());
        assertTrue(MaterialMatcher.findExact(fixture(), norm(null)).isEmpty());
    }

    // ------------------------------------------------------------------
    // 归一化差异不该影响匹配
    // ------------------------------------------------------------------

    @Test
    @DisplayName("全角/空格/大小写差异不阻碍命中（识别结果常夹带这些）")
    void tolerantToNormalizableDifferences() {
        assertEquals(1, MaterialMatcher.findExact(fixture(), norm(" 电 石 ")).size());
        assertEquals(1, MaterialMatcher.findExact(fixture(), norm("三氯氢硅　")).size());
        assertEquals(1, MaterialMatcher.findExact(fixture(), norm("hnd-v150")).size());
    }

    // ------------------------------------------------------------------
    // 候选排序
    // ------------------------------------------------------------------

    @Test
    @DisplayName("候选把全等项排在最前，其后按名称长度接近度 —— 人一眼就能选中")
    void exactRankedFirst() {
        List<MaterialMaster> ranked = MaterialMatcher.rank(fixture(), norm("HND-2171"), 20, false);

        assertFalse(ranked.isEmpty());
        assertEquals("114002486", ranked.get(0).getMaterialCode(), "全等的 HND-2171 应排最前");
    }

    @Test
    @DisplayName("候选带出「电石渣」供人工分辨 —— 查「电石」时它应出现在候选里，但绝不自动填")
    void ambiguousSiblingsAppearAsCandidates() {
        List<MaterialMaster> ranked = MaterialMatcher.rank(fixture(), norm("电石"), 20, false);

        assertTrue(ranked.stream().anyMatch(m -> "114001903".equals(m.getMaterialCode())),
                "「电石渣」应作为候选出现，由人工决定是不是它");
        assertEquals("111001786", ranked.get(0).getMaterialCode(), "但排最前的必须是精确匹配的「电石」");
    }

    @Test
    @DisplayName("limit 生效，避免把 244 条全甩给前端")
    void respectsLimit() {
        assertEquals(2, MaterialMatcher.rank(fixture(), norm("hnd"), 2, false).size());
    }

    @Test
    @DisplayName("★ OCR 误读仍要给得出候选 —— 「三砖」查不到三氯氢硅，但必须让人工有线索")
    void misreadStillYieldsCandidates() {
        List<MaterialMaster> ranked = MaterialMatcher.rank(fixture(), norm("三砖"), 20, false);

        assertFalse(ranked.isEmpty(), "误读时若候选为空，人工就无从下手");
        assertTrue(ranked.stream().anyMatch(m -> "三氯氢硅".equals(m.getMaterialName())),
                "「三砖」应能把含「三」的三氯氢硅列进候选");
        assertTrue(MaterialMatcher.findExact(fixture(), norm("三砖")).isEmpty(),
                "但绝不能自动填 —— 候选只供人工挑");
    }

    @Test
    @DisplayName("规格不参与自动匹配 —— 字符只出现在规格里时，自动匹配不应返回该物料")
    void specNotUsedForAutoMatch() {
        List<MaterialMaster> withSpec = List.of(material("900000001", "碳酸钠", "25kg/袋"));

        // 「袋」只出现在规格里，名称中没有
        assertTrue(MaterialMatcher.rank(withSpec, norm("袋"), 20, false).isEmpty());
    }

    @Test
    @DisplayName("人工检索时规格参与匹配 —— 便于按规格回忆物料")
    void specUsedForManualSearch() {
        List<MaterialMaster> withSpec = List.of(material("900000001", "碳酸钠", "25kg/袋"));

        List<MaterialMaster> ranked = MaterialMatcher.rank(withSpec, norm("袋"), 20, true);

        assertFalse(ranked.isEmpty(), "人工检索应能通过规格找到物料");
        assertEquals("900000001", ranked.get(0).getMaterialCode());
    }

    @Test
    @DisplayName("人工检索支持按编码 —— 人可能记得编码而不记得名称")
    void manualSearchMatchesByCode() {
        List<MaterialMaster> ranked = MaterialMatcher.rank(fixture(), norm("111001786"), 20, true);

        assertFalse(ranked.isEmpty());
        assertEquals("111001786", ranked.get(0).getMaterialCode());
    }

    @Test
    @DisplayName("自动匹配【不】认编码 —— findExact 只比名称，防止把数字误当编码填进去")
    void autoMatchIgnoresCode() {
        assertTrue(MaterialMatcher.findExact(fixture(), norm("111001786")).isEmpty());
    }

    @Test
    @DisplayName("★ 搜「V150 200」不得出现 D150 / D2150 —— 型号是标识符，不是模糊文本")
    void searchExcludesOtherModels() {
        // 用户在搜索框里敲空格，主数据里是下划线；不压分隔符的话两者永远对不上，
        // 所有结果都会掉到模糊档，而 V150 / D150 / D2150 只差一两个字符，会被混排
        List<MaterialMaster> ranked = MaterialMatcher.rank(fixture(), norm("V150 200"), 20, true);

        assertFalse(ranked.isEmpty(), "应命中 V150 的 200kg 包装");
        assertTrue(ranked.stream().noneMatch(m -> m.getMaterialName().startsWith("HND-D")),
                "D150 / D2150 是别的产品，不该出现在结果里："
                        + ranked.stream().map(MaterialMaster::getMaterialName).toList());
        assertEquals("115055980", ranked.get(0).getMaterialCode());
    }

    @Test
    @DisplayName("★ 兜底档仍要生效：一个型号都对不上时（手写误读），必须给得出线索")
    void fallsBackToLooseCandidatesWhenNothingAligns() {
        // 「三砖」是 OCR 对「三氯氢硅」的误读：型号对不上、也不是任何名称的子串，
        // 此时不能返回空 —— 人工需要线索（详见 8.9 与 8.6）
        List<MaterialMaster> ranked = MaterialMatcher.rank(fixture(), norm("三砖"), 20, false);

        assertFalse(ranked.isEmpty(), "误读时若候选为空，人工就无从下手");
        assertTrue(ranked.stream().anyMatch(m -> "三氯氢硅".equals(m.getMaterialName())));
        assertTrue(MaterialMatcher.findExact(fixture(), norm("三砖")).isEmpty(),
                "但绝不能自动填 —— 候选只供人工挑");
    }

    @Test
    @DisplayName("有明确结果时不再掺兜底档 —— 否则「结果里混着别的东西」")
    void doesNotMixLooseCandidatesWhenStrictMatchesExist() {
        // 「硅粉」「三氯氢硅」共享「硅」字，走兜底档时两者会一起出现；有明确命中时不该再掺进来
        List<MaterialMaster> ranked = MaterialMatcher.rank(fixture(), norm("三氯氢硅"), 20, true);

        assertEquals(1, ranked.size(), "只该有「三氯氢硅」一条，不该再掺入字符相近的其他物料");
        assertEquals("111001787", ranked.get(0).getMaterialCode());
    }

    @Test
    @DisplayName("排序稳定：同一关键词多次调用返回顺序一致")
    void rankingIsStable() {
        List<String> first = MaterialMatcher.rank(fixture(), norm("hnd"), 20, false)
                .stream().map(m -> m.getMaterialCode() + "|" + m.getMaterialName()).toList();
        List<String> second = MaterialMatcher.rank(fixture(), norm("hnd"), 20, false)
                .stream().map(m -> m.getMaterialCode() + "|" + m.getMaterialName()).toList();

        assertEquals(first, second);
    }
}
