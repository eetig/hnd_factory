package org.example.service.impl;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.dto.EquipmentLedgerQueryDTO;
import org.example.dto.EquipmentLedgerVO;
import org.example.dto.EquipmentSaveDTO;
import org.example.dto.PageQueryDTO;
import org.example.dto.VesselVO;
import org.example.entity.EquipmentLedger;
import org.example.mapper.EquipmentLedgerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 体积计算页的容器列表（{@code /api/equipment/vessels}）单测，变更-025。
 *
 * <p>这段逻辑里两处容易写错、写错又不容易发现，都钉在这里：
 * <ol>
 *   <li><b>筛选条件是「几何 + 底图填齐」</b>：台账 91 行里只有个位数填了这些列，
 *       条件一旦漏掉一个（比如只判内径），就会把没底图、没坐标的行放进下拉，
 *       前端拿到后画不出图、算不对体积 —— 而且不报错，只是不对。</li>
 *   <li><b>排序必须走拼音 Collator</b>：与 {@code /search} 同一个口径。
 *       交给库的排序规则会变成码点序（三 &lt; 前 &lt; 反），人看不出规律。</li>
 * </ol>
 *
 * <p>筛选本身在 SQL 侧（与既有 search 的分工一致），所以这里不断言「哪些行被排除」，
 * 而是断言**生成的 WHERE 里确实带了这几条**；映射与排序在 Java 侧，直接断言结果。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquipmentLedgerServiceImplTest {

    @Mock
    private EquipmentLedgerMapper baseMapper;

    private EquipmentLedgerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new EquipmentLedgerServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        // 纯单测里 MyBatis-Plus 没初始化表元数据，Lambda 的「方法引用 → 列名」解析会抛
        // MybatisPlusException，导致读不出生成的 WHERE。手工注册一次即可。
        // （本工程其它单测不这么做，是因为它们不检查 SQL；这里要钉住筛选条件，所以需要。）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), EquipmentLedger.class);
    }

    /** 一台几何填齐的容器 */
    private static EquipmentLedger vessel(Long id, String name) {
        EquipmentLedger row = new EquipmentLedger();
        row.setId(id);
        row.setEquipmentName(name);
        row.setEquipmentCode("HND-V" + id);
        row.setWorkshop("罐区");
        row.setContainerType(1);                       // 卧式
        row.setInnerDiameter(new BigDecimal("2200"));
        row.setShellLength(new BigDecimal("4000"));
        row.setStraightFlange(new BigDecimal("40"));
        row.setTopHeadDepth(new BigDecimal("550"));
        row.setBottomHeadDepth(new BigDecimal("550"));
        row.setImageFile("vessel.png");
        row.setImageBounds("{\"width\":1.0,\"height\":0.5}");
        row.setMedium("甲醇");
        row.setDensity(new BigDecimal("0.792"));
        row.setHeadVolume(new BigDecimal("1.5459"));
        return row;
    }

    @Test
    @DisplayName("映射：几何、底图、介质密度、对账基准都带出来")
    void mapsAllFieldsThePageNeeds() {
        when(baseMapper.selectList(any())).thenReturn(List.of(vessel(7L, "甲醇计量罐")));

        List<VesselVO> list = service.listVessels();

        assertEquals(1, list.size());
        VesselVO vo = list.get(0);
        assertEquals(7L, vo.getId());
        assertEquals("甲醇计量罐", vo.getName());
        assertEquals("HND-V7", vo.getCode());
        assertEquals(Integer.valueOf(1), vo.getContainerType());
        assertEquals(0, new BigDecimal("2200").compareTo(vo.getDiameter()));
        assertEquals(0, new BigDecimal("4000").compareTo(vo.getShellLength()));
        assertEquals(0, new BigDecimal("40").compareTo(vo.getStraightFlange()));
        assertEquals(0, new BigDecimal("550").compareTo(vo.getTopHeadDepth()));
        assertEquals(0, new BigDecimal("550").compareTo(vo.getBottomHeadDepth()));
        assertEquals("vessel.png", vo.getImageFile());
        assertEquals("{\"width\":1.0,\"height\":0.5}", vo.getImageBounds());
        assertEquals("甲醇", vo.getMedium());
        assertEquals(0, new BigDecimal("0.792").compareTo(vo.getDensity()));
        // 对账基准：前端拿它比对按几何算出的封头容积，差得多说明几何填错了
        assertEquals(0, new BigDecimal("1.5459").compareTo(vo.getHeadVolume()));
    }

    @Test
    @DisplayName("立式平底：没有下封头时该字段为 null（前端据此判定「平底」）")
    void keepsNullBottomHead() {
        EquipmentLedger flat = vessel(9L, "150产品罐");
        flat.setBottomHeadDepth(null);
        when(baseMapper.selectList(any())).thenReturn(List.of(flat));

        VesselVO vo = service.listVessels().get(0);

        assertNull(vo.getBottomHeadDepth(), "平底容器必须留 null，不能填 0 以外的东西");
    }

    @Test
    @DisplayName("排序按拼音，与 /search 同一口径（反 < 前 < 三，不是码点的 三 < 前 < 反）")
    void sortsByPinyinNotCodePoint() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                vessel(1L, "三甲加料罐"),
                vessel(2L, "前分罐"),
                vessel(3L, "反应物接受罐")));

        List<String> names = service.listVessels().stream().map(VesselVO::getName).toList();

        assertEquals(List.of("反应物接受罐", "前分罐", "三甲加料罐"), names);
    }

    @Test
    @DisplayName("筛选落在 SQL 侧：WHERE 里必须带齐「几何 + 底图填齐」的每一条")
    void filtersIncompleteRowsInSql() {
        when(baseMapper.selectList(any())).thenReturn(List.of());

        service.listVessels();

        ArgumentCaptor<LambdaQueryWrapper<EquipmentLedger>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(baseMapper).selectList(captor.capture());
        // 用 getSqlSegment() 而不是 getTargetSql()：后者要表元数据（TableInfo），
        // 纯单测里没初始化会抛 MybatisPlusException。这里只需要看到「WHERE 里带了哪些列」，
        // 占位符不影响判断。
        String sql = captor.getValue().getSqlSegment();

        assertTrue(sql.contains("enabled"), "必须只取启用的行：" + sql);
        // 少任何一条，都会把「没填几何/没配底图」的行放进下拉 —— 那种行前端画不出图
        assertTrue(sql.contains("inner_diameter"), "缺内径条件：" + sql);
        assertTrue(sql.contains("shell_length"), "缺筒体长条件：" + sql);
        assertTrue(sql.contains("top_head_depth"), "缺上封头深条件：" + sql);
        assertTrue(sql.contains("image_file"), "缺底图文件名条件：" + sql);
        assertTrue(sql.contains("image_bounds"), "缺底图坐标条件：" + sql);
        // 底图文件名是字符串列，空串也得排掉（json 列不可能为空串，故不加这一条）
        assertTrue(sql.contains("<>"), "底图文件名的空串没有被排除：" + sql);
        assertFalse(sql.contains("limit"), "本列表不该有 LIMIT，候选就那么多：" + sql);
    }
}

/**
 * 「设备数据维护」页的三个服务方法（2026-10-06 起本表不再只读）。
 *
 * <p>钉两处最容易写错、写错又不报错的地方：
 * <ol>
 *   <li><b>新增时的 source_row 必须取「同工作表最大值 +1」</b>（`uk_source_row` 唯一键的一半）——
 *       写死 0 或 1 的话第二次新增就撞唯一键，而报错信息是数据库层的，很难联想到这。</li>
 *   <li><b>清空某列要真的写进去</b>（如把下封头深度清成 NULL 表示平底）。这依赖实体上的
 *       `@TableField(updateStrategy = ALWAYS)`；默认策略只写非 null 字段，
 *       页面上会「看着改成功了、库里没动」。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquipmentLedgerMaintenanceTest {

    @Mock
    private EquipmentLedgerMapper baseMapper;

    private EquipmentLedgerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new EquipmentLedgerServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        // 停用/启用那条定向 UPDATE 要看 SET 段里有哪些列，而 Lambda 的
        // 「方法引用 → 列名」解析需要表元数据（纯单测里 MyBatis-Plus 不会自己初始化）。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), EquipmentLedger.class);
    }

    private static EquipmentSaveDTO draft() {
        EquipmentSaveDTO dto = new EquipmentSaveDTO();
        // 位号是库层 NOT NULL 列之一，且 变更-065 起在服务端先校验 —— 正常请求必然带它
        dto.setEquipmentCode("HND-150-01");
        dto.setEquipmentName("甲醇计量罐");
        dto.setNickname("甲醇罐");
        dto.setWorkshop("罐区");
        dto.setSpec("DN2300/DN2200x3400（夹套）");
        dto.setContainerType(3);
        dto.setInnerDiameter(new BigDecimal("2200"));
        dto.setShellLength(new BigDecimal("3400"));
        dto.setStraightFlange(new BigDecimal("40"));
        dto.setTopHeadDepth(new BigDecimal("550"));
        dto.setBottomHeadDepth(new BigDecimal("550"));
        dto.setMedium("甲醇");
        dto.setDensity(new BigDecimal("0.792"));
        dto.setImageFile("vessel-methanol.png");
        return dto;
    }

    @Test
    @DisplayName("新增：source_sheet 记 manual、source_row = 同工作表最大值 + 1")
    void createsWithNextManualSourceRow() {
        when(baseMapper.selectObjs(any())).thenReturn(List.of(7)); // 该工作表已有 7 行
        when(baseMapper.insert(any(EquipmentLedger.class))).thenAnswer(inv -> {
            inv.getArgument(0, EquipmentLedger.class).setId(548L);
            return 1;
        });

        Long id = service.save(draft());

        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).insert(captor.capture());
        EquipmentLedger saved = captor.getValue();
        assertEquals(548L, id, "应当回传自增出来的新 id");
        assertEquals("manual", saved.getSourceSheet());
        assertEquals(8, saved.getSourceRow(), "必须接在最大值后面，否则第二次新增就撞唯一键");
        assertEquals(1, saved.getEnabled(), "新增的行默认启用");
        assertEquals("甲醇罐", saved.getNickname());
    }

    @Test
    @DisplayName("更新：走 updateById，且可编辑字段逐项带过去")
    void updatesById() {
        // 0 行现在会报「设备不存在」（变更-065），所以正常路径必须打桩成 1
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);
        EquipmentSaveDTO dto = draft();
        dto.setId(37L);
        dto.setNickname("改过的昵称");

        Long id = service.save(dto);

        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).updateById(captor.capture());
        EquipmentLedger row = captor.getValue();
        assertEquals(37L, id);
        assertEquals(37L, row.getId());
        assertEquals("改过的昵称", row.getNickname());
        assertEquals(new BigDecimal("2200"), row.getInnerDiameter());
        assertEquals("甲醇", row.getMedium());
        // 新增路径不该被走到
        verify(baseMapper, never()).insert(any(EquipmentLedger.class));
    }

    @Test
    @DisplayName("清空某列时真的写 NULL（依赖 updateStrategy=ALWAYS，默认策略会静默丢掉）")
    void clearsColumnToNull() {
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);
        EquipmentSaveDTO dto = draft();
        dto.setId(89L);
        dto.setBottomHeadDepth(null); // 改成平底
        dto.setNickname(null);

        service.save(dto);

        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).updateById(captor.capture());
        assertNull(captor.getValue().getBottomHeadDepth(), "清下封头深度必须真的写 NULL");
        assertNull(captor.getValue().getNickname());
    }

    @Test
    @DisplayName("可编辑字段都标了 updateStrategy=ALWAYS（清空某列靠的就是它）")
    void editableFieldsAlwaysUpdate() throws Exception {
        // 少了这个注解，updateById 会跳过值为 null 的字段 —— 表现是「页面上改成功了、库里没动」，
        // 而且只有「清空」这一种操作会中招，极难发现。所以直接钉注解本身。
        for (String name : List.of("equipmentCode", "equipmentName", "nickname", "workshop", "spec",
                "containerType", "innerDiameter", "shellLength", "straightFlange", "topHeadDepth",
                "bottomHeadDepth", "imageFile", "volumePerMm", "density", "medium", "remark")) {
            Field field = EquipmentLedger.class.getDeclaredField(name);
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation, name + " 没有 @TableField 注解");
            assertEquals(FieldStrategy.ALWAYS, annotation.updateStrategy(),
                    name + " 的 updateStrategy 不是 ALWAYS —— 清空这一列会静默不生效");
        }
        // 反向：底图坐标**开放写入，但不随普通保存被覆写**（变更-081 的策略变更）。
        // 它现在是可编辑字段（维护页的「标定底图」要写它），但刻意不标 ALWAYS ——
        // 标了之后，「保存表单」没带这一列时会把已标定的坐标清成 NULL。
        assertNull(EquipmentLedger.class.getDeclaredField("imageBounds").getAnnotation(TableField.class),
                "imageBounds 不该带 updateStrategy=ALWAYS：那会让普通保存把标定结果清空");
    }

    @Test
    @DisplayName("停用/启用：只 SET enabled 一列 —— 不许再用半实体 updateById")
    @SuppressWarnings("unchecked")
    void togglesEnabledWithTargetedUpdate() {
        // 0 行现在会报「设备不存在」（变更-065）；这条测的是 SQL 形状，走正常路径
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        service.setEnabled(89L, false);

        /*
         * 这条测试曾把错误实现钉住：原先写的是「半实体 + updateById」，注释还说
         * 「别的字段保持 null 不被写出」—— 事实相反：可编辑字段都标了
         * updateStrategy = ALWAYS，MyBatis-Plus 会把它们**一并写进 SET** 成为 NULL。
         * 2026-10-15 的现场就是 `equipment_code = NULL` 撞 NOT NULL 约束，整页「停用」
         * 全失败，而前端只看到一句 403（真实 DB 错被 ERROR 派发里的 Sa-Token 异常盖掉）。
         * 所以这里从「断言实体字段」改成**断言生成的 SQL**：SET 段只许有 enabled。
         */
        verify(baseMapper, never()).updateById(any(EquipmentLedger.class));

        ArgumentCaptor<LambdaUpdateWrapper<EquipmentLedger>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<EquipmentLedger> wrapper = captor.getValue();

        String setSql = wrapper.getSqlSet();
        assertTrue(setSql.contains("enabled"), "SET 段必须写 enabled：" + setSql);
        for (String column : List.of("equipment_code", "equipment_name", "nickname", "spec",
                "workshop", "container_type", "inner_diameter", "shell_length", "medium",
                "density", "remark")) {
            assertFalse(setSql.contains(column),
                    "SET 段混进了 " + column + " —— ALWAYS 策略会把它写成 NULL，撞库的 NOT NULL 约束：" + setSql);
        }
        assertTrue(wrapper.getSqlSegment().contains("id"),
                "WHERE 必须按 id 定位：" + wrapper.getSqlSegment());
    }

    @Test
    @DisplayName("维护页列表：默认**不**排除停用的行（停用的要能重新启用），也不带关键字条件")
    void listPageKeepsDisabledRowsByDefault() {
        EquipmentLedger disabled = new EquipmentLedger();
        disabled.setId(9L);
        disabled.setEquipmentName("停用的罐");
        disabled.setEnabled(0);
        stubSelectPage(List.of(disabled));

        IPage<EquipmentLedgerVO> page = service.listPage(ledgerQuery(1, null, null));

        ArgumentCaptor<QueryWrapper<EquipmentLedger>> captor = wrapperCaptor();
        verify(baseMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();

        // 与 search / listVessels 不同：那两个要排掉停用的行，这个不能排 ——
        // 排掉之后维护页就再也启用不回来了（页面上根本看不到那一行）
        assertFalse(sql.contains("enabled"), "默认不该过滤启用状态：" + sql);
        assertFalse(sql.contains("LIKE"), "没给关键字时不该有模糊条件：" + sql);
        assertEquals(1, page.getRecords().size());
        assertEquals(1L, page.getTotal());
    }

    @Test
    @DisplayName("维护页列表：显式要求「只看启用的」才加上 enabled 条件")
    void listPageFiltersEnabledWhenAsked() {
        stubSelectPage(List.of());

        service.listPage(ledgerQuery(1, true, null));

        ArgumentCaptor<QueryWrapper<EquipmentLedger>> captor = wrapperCaptor();
        verify(baseMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();

        assertTrue(sql.contains("enabled"), "只看启用时必须带上 enabled 条件：" + sql);
    }

    @Test
    @DisplayName("维护页列表：关键字命中五个字段（漏一列就会「明明有这个设备却搜不到」）")
    void listPageKeywordCoversAllFiveColumns() {
        stubSelectPage(List.of());

        service.listPage(ledgerQuery(1, true, "三甲"));

        ArgumentCaptor<QueryWrapper<EquipmentLedger>> captor = wrapperCaptor();
        verify(baseMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();

        for (String column : List.of("equipment_code", "equipment_name", "nickname", "spec", "workshop")) {
            assertTrue(sql.contains(column), "关键字漏了 " + column + " 列：" + sql);
        }
        // 括号：不包的话 or 会与 enabled 条件平级，变成「只看启用的 或 名字含关键字」，
        // 停用的行会被一起带出来
        assertTrue(sql.contains("("), "or 段必须被括号包住：" + sql);
    }

    @Test
    @DisplayName("维护页列表：分页参数按归一化后的值传给查询（页码越界回第 1 页、条数超上限截断）")
    void listPagePassesNormalizedPaging() {
        stubSelectPage(List.of());

        EquipmentLedgerQueryDTO query = ledgerQuery(0, null, null);
        query.setPageSize(9999);
        service.listPage(query);

        ArgumentCaptor<IPage<EquipmentLedger>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(baseMapper).selectPage(captor.capture(), any());
        assertEquals(1L, captor.getValue().getCurrent(), "页码 0 应当归一到第 1 页");
        assertEquals((long) PageQueryDTO.MAX_PAGE_SIZE, captor.getValue().getSize(),
                "pageSize 超上限必须截断，否则手搓一个大值就能把全表拉回来");
    }

    // ================= 变更-065：整行覆写契约的三道机器校验 =================

    @Test
    @DisplayName("字段清单一致性：DTO 字段集 = 可编辑清单键集 ⊇ 实体上标 ALWAYS 的列集")
    void editableFieldSetsAgree() {
        // 为什么必须有这条：save() 的编辑分支是**整行覆写**，靠的就是实体上的 ALWAYS 策略。
        // 将来在实体上多标一个 ALWAYS 列而忘了加进 DTO 白名单，那条路径就会把它**静默清空**，
        // 而所有既有测试都不会失败。把「三处字段清单要一起改」从注释里的约定变成机器校验。
        Set<String> alwaysFields = Arrays.stream(EquipmentLedger.class.getDeclaredFields())
                .filter(f -> Optional.ofNullable(f.getAnnotation(TableField.class))
                        .map(t -> t.updateStrategy() == FieldStrategy.ALWAYS).orElse(false))
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(alwaysFields.isEmpty(), "没扫到 ALWAYS 列，说明注解形态变了，这条校验需要更新");

        Set<String> dtoFields = Arrays.stream(EquipmentSaveDTO.class.getDeclaredFields())
                .map(Field::getName)
                .filter(name -> !"id".equals(name))
                .collect(Collectors.toSet());

        // ① 清单键集 = DTO 字段集。比原先的「反射逐字段对 getter」更紧：那时只能发现
        //    「清单漏了字段」，发现不了「清单里留着 DTO 已经没有的死键」。
        Set<String> whitelist = EquipmentLedgerServiceImpl.EDITABLE_FIELDS.keySet();
        assertEquals(dtoFields, whitelist,
                "白名单与 EquipmentSaveDTO 的字段集不一致：DTO 有而清单没有=" + minus(dtoFields, whitelist)
                        + "，清单有而 DTO 没有=" + minus(whitelist, dtoFields));

        // ② ALWAYS 列必须都能搬，否则整行覆写会把它清空
        assertTrue(whitelist.containsAll(alwaysFields),
                "实体 ALWAYS 列里有 DTO 表达不了的字段，整行覆写会把它清空："
                        + minus(alwaysFields, whitelist));
    }

    @Test
    @DisplayName("更新：16 个可编辑字段逐个搬到实体上（含数字列 —— 原先只给字符串列赋了值）")
    void updateCopiesEveryEditableField() {
        // 原实现用反射调私有 applyEditableFields，并且只给 String 字段赋了值：数字列的 setter
        // 漏掉时两侧都是 null，断言照样通过。这里走公开的 save()，且每个字段给不同值。
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);

        EquipmentSaveDTO dto = new EquipmentSaveDTO();
        dto.setId(37L);
        dto.setEquipmentCode("HND-150-01");
        dto.setEquipmentName("甲醇计量罐");
        dto.setNickname("甲醇罐");
        dto.setWorkshop("罐区");
        dto.setSpec("DN2300/DN2200x3400（夹套）");
        dto.setContainerType(3);
        dto.setInnerDiameter(new BigDecimal("2200"));
        dto.setShellLength(new BigDecimal("3400"));
        dto.setStraightFlange(new BigDecimal("40"));
        dto.setTopHeadDepth(new BigDecimal("550"));
        dto.setBottomHeadDepth(new BigDecimal("550"));
        dto.setVolumePerMm(new BigDecimal("0.000004"));
        dto.setDensity(new BigDecimal("0.792"));
        dto.setMedium("甲醇");
        dto.setRemark("现场复核过");
        dto.setImageFile("/files/abc.png");

        service.save(dto);

        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).updateById(captor.capture());
        EquipmentLedger row = captor.getValue();

        assertEquals("HND-150-01", row.getEquipmentCode());
        assertEquals("甲醇计量罐", row.getEquipmentName());
        assertEquals("甲醇罐", row.getNickname());
        assertEquals("罐区", row.getWorkshop());
        assertEquals("DN2300/DN2200x3400（夹套）", row.getSpec());
        assertEquals(3, row.getContainerType().intValue());
        assertEquals(0, new BigDecimal("2200").compareTo(row.getInnerDiameter()));
        assertEquals(0, new BigDecimal("3400").compareTo(row.getShellLength()));
        assertEquals(0, new BigDecimal("40").compareTo(row.getStraightFlange()));
        assertEquals(0, new BigDecimal("550").compareTo(row.getTopHeadDepth()));
        assertEquals(0, new BigDecimal("550").compareTo(row.getBottomHeadDepth()));
        assertEquals(0, new BigDecimal("0.000004").compareTo(row.getVolumePerMm()));
        assertEquals(0, new BigDecimal("0.792").compareTo(row.getDensity()));
        assertEquals("甲醇", row.getMedium());
        assertEquals("现场复核过", row.getRemark());
        assertEquals("/files/abc.png", row.getImageFile());
    }

    @Test
    @DisplayName("编辑：设备名称为空才拒；位号/车间允许空串（变更-085 更正了原先自相矛盾的规则）")
    void saveRejectsOnlyEmptyName() {
        EquipmentSaveDTO dto = new EquipmentSaveDTO();
        dto.setId(1L);
        dto.setEquipmentCode("HND-V1");
        dto.setWorkshop("罐区");
        // 名称缺失 → 拒（它是人认设备的唯一标识）
        BusinessException e = assertThrows(BusinessException.class, () -> service.save(dto));
        assertEquals("设备名称不能为空", e.getMessage());
        verify(baseMapper, never()).updateById(any(EquipmentLedger.class));

        // 名称是空白串 → 同样拒
        dto.setEquipmentName("   ");
        assertEquals("设备名称不能为空",
                assertThrows(BusinessException.class, () -> service.save(dto)).getMessage());
    }

    @Test
    @DisplayName("★ 回归（变更-085）：位号/车间为空的行必须能存进去 —— 生产上 92 行都是空的")
    void saveAcceptsRowsWithoutCodeAndWorkshop() {
        // 生产库真实形态：92 行全部 equipment_code='' 且 workshop=''
        // （库定义 NOT NULL DEFAULT ''，源台账里"整页没有位号"的容器有意留空）
        EquipmentSaveDTO dto = new EquipmentSaveDTO();
        dto.setId(680L);
        dto.setEquipmentCode(null);
        dto.setWorkshop(null);
        dto.setEquipmentName("三甲粗品罐");
        dto.setInnerDiameter(new java.math.BigDecimal("1600"));
        dto.setShellLength(new java.math.BigDecimal("2000"));
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);

        service.save(dto);

        // null 被归一成空串（不撞 NOT NULL），几何照常写下去
        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).updateById(captor.capture());
        EquipmentLedger written = captor.getValue();
        assertEquals("", written.getEquipmentCode());
        assertEquals("", written.getWorkshop());
        assertEquals("三甲粗品罐", written.getEquipmentName());
        assertEquals(0, new java.math.BigDecimal("1600").compareTo(written.getInnerDiameter()));
    }

    @Test
    @DisplayName("编辑：改 0 行 = 该 id 不存在，明确报错（原先静默当成功）")
    void saveReportsMissingRow() {
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(0);
        EquipmentSaveDTO dto = new EquipmentSaveDTO();
        dto.setId(1L);
        dto.setEquipmentCode("HND-V1");
        dto.setEquipmentName("150产品罐");
        dto.setWorkshop("罐区");
        BusinessException e = assertThrows(BusinessException.class, () -> service.save(dto));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
    }

    @Test
    @DisplayName("停用/启用：改 0 行同样明确报错，不再回一个假的\"成功\"")
    void setEnabledReportsMissingRow() {
        when(baseMapper.update(isNull(), any())).thenReturn(0);
        BusinessException e = assertThrows(BusinessException.class, () -> service.setEnabled(404L, false));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
    }

    // ================= 变更-081：底图坐标写入 + 启用门槛 =================

    /** 与生产那 4 行同形的一台齐备容器（立式、有上下封头、底图带上下切线） */
    private static EquipmentLedger completeVessel(Long id) {
        EquipmentLedger row = new EquipmentLedger();
        row.setId(id);
        row.setEquipmentName("甲醇计量罐");
        row.setEquipmentCode("HND-V" + id);
        row.setWorkshop("罐区");
        row.setContainerType(3);
        row.setInnerDiameter(new BigDecimal("2200"));
        row.setShellLength(new BigDecimal("3400"));
        row.setStraightFlange(new BigDecimal("40"));
        row.setTopHeadDepth(new BigDecimal("550"));
        row.setBottomHeadDepth(new BigDecimal("550"));
        row.setImageFile("vessel-methanol.png");
        row.setImageBounds("{\"top\":83,\"left\":72,\"right\":627.5,\"width\":708,"
                + "\"bottom\":1247,\"height\":1520,\"tangent\":218,\"tangentBottom\":1114}");
        return row;
    }

    @Test
    @DisplayName("启用门槛：不合规的行拒绝启用，一次说全缺什么，且**不发 UPDATE**")
    void enableGateRejectsIncompleteRow() {
        EquipmentLedger incomplete = new EquipmentLedger();
        incomplete.setId(680L);
        incomplete.setEquipmentName("三甲粗品罐");
        incomplete.setContainerType(3);
        when(baseMapper.selectById(680L)).thenReturn(incomplete);

        BusinessException e = assertThrows(BusinessException.class, () -> service.setEnabled(680L, true));

        assertTrue(e.getMessage().contains("未配置容器底图"), e.getMessage());
        assertTrue(e.getMessage().contains("底图未标定坐标"), e.getMessage());
        assertTrue(e.getMessage().contains("未填内径"), e.getMessage());
        // 关键：被拒时不能已经改了状态 —— 否则使用者看到"失败"但库里已经启用了
        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("启用门槛：齐备的行照常启用（不会把在用的设备挡住）")
    void enableGateAllowsCompleteRow() {
        when(baseMapper.selectById(674L)).thenReturn(completeVessel(674L));
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        service.setEnabled(674L, true);

        verify(baseMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("停用：永不拦截，也不查门槛（否则不合规的行会被永久锁在启用状态）")
    void disablingIsNeverBlocked() {
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        service.setEnabled(680L, false);

        verify(baseMapper, never()).selectById(any());
        verify(baseMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("保存：底图坐标非法直接拒（文案可展示）；合法则原样存进实体")
    void saveValidatesImageBounds() {
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);

        EquipmentSaveDTO bad = draft();
        bad.setId(674L);
        bad.setImageBounds("{\"width\":708,\"height\":1520}");   // 缺轮廓框
        BusinessException e = assertThrows(BusinessException.class, () -> service.save(bad));
        assertTrue(e.getMessage().contains("left"), e.getMessage());
        verify(baseMapper, never()).updateById(any(EquipmentLedger.class));

        EquipmentSaveDTO good = draft();
        good.setId(674L);
        good.setImageBounds("{\"width\":708,\"height\":1520,\"left\":72,\"right\":627.5,"
                + "\"top\":83,\"bottom\":1247,\"tangent\":218,\"tangentBottom\":1114}");
        service.save(good);

        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(good.getImageBounds(), captor.getValue().getImageBounds(),
                "坐标应当原样存下来（不做键序/数字写法的规范化）");
    }

    @Test
    @DisplayName("保存：没带坐标时不写这一列（否则普通保存会把标定结果清空）")
    void saveWithoutBoundsLeavesItAlone() throws Exception {
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);
        EquipmentSaveDTO dto = draft();
        dto.setId(674L);
        dto.setImageBounds(null);          // 表单没带（它只改几何）

        service.save(dto);

        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).updateById(captor.capture());
        assertNull(captor.getValue().getImageBounds());
        // 「库里原值保住」靠的是实体上没有 ALWAYS 策略：MyBatis-Plus 默认只写非 null 字段。
        // 这一条与 editableFieldsAlwaysUpdate 的反向断言配对 —— 那边钉注解，这里钉行为。
        Field field = EquipmentLedger.class.getDeclaredField("imageBounds");
        assertNull(field.getAnnotation(TableField.class),
                "imageBounds 一旦带上 ALWAYS 策略，普通保存就会把标定坐标写成 NULL");
    }

    @Test
    @DisplayName("维护页列表：每行带「不能启用的原因」；齐备的行为 null")
    void listPageExposesEnableBlockReason() {
        EquipmentLedger bad = new EquipmentLedger();
        bad.setId(680L);
        bad.setEquipmentName("三甲粗品罐");
        bad.setEnabled(1);
        stubSelectPage(List.of(bad, completeVessel(674L)));

        IPage<EquipmentLedgerVO> page = service.listPage(ledgerQuery(1, null, null));

        String reason = page.getRecords().get(0).getEnableBlockReason();
        assertNotNull(reason, "缺底图的行必须给出原因，否则前端只能显示一个点了没反应的按钮");
        assertTrue(reason.startsWith("不能启用："), reason);
        assertNull(page.getRecords().get(1).getEnableBlockReason(), "齐备的行不该有原因");
    }

    @Test
    @DisplayName("换底图且没带新坐标 → 定向清空旧坐标（旧坐标画到新图上液面就错，而且不报错）")
    void changingDrawingClearsStaleBounds() {
        when(baseMapper.selectById(674L)).thenReturn(completeVessel(674L));
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        EquipmentSaveDTO dto = draft();
        dto.setId(674L);
        dto.setImageFile("/files/new-drawing.png");   // 与库里的 vessel-methanol.png 不同
        dto.setImageBounds(null);

        service.save(dto);

        // 清空那一条：SET 段只有 image_bounds（避免拿半实体 updateById 把别的列也写一遍）
        verify(baseMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("底图没变 → 不动坐标（反复保存同一个表单不该把标定清掉）")
    void sameDrawingKeepsBounds() {
        when(baseMapper.selectById(674L)).thenReturn(completeVessel(674L));
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);

        EquipmentSaveDTO dto = draft();
        dto.setId(674L);
        dto.setImageFile("vessel-methanol.png");      // 与库里同名
        dto.setImageBounds(null);

        service.save(dto);

        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("换底图但带了新坐标 → 存新坐标，且不必多读一次库")
    void newDrawingWithNewBoundsSkipsClear() {
        when(baseMapper.updateById(any(EquipmentLedger.class))).thenReturn(1);

        EquipmentSaveDTO dto = draft();
        dto.setId(674L);
        dto.setImageFile("/files/new.png");
        dto.setImageBounds("{\"width\":100,\"height\":200,\"left\":1,\"right\":99,\"top\":2,\"bottom\":198}");

        service.save(dto);

        verify(baseMapper, never()).update(isNull(), any());
        verify(baseMapper, never()).selectById(674L);
    }

    private static Set<String> minus(Set<String> left, Set<String> right) {
        Set<String> diff = new java.util.LinkedHashSet<>(left);
        diff.removeAll(right);
        return diff;
    }

    private static EquipmentLedgerQueryDTO ledgerQuery(Integer pageNum, Boolean onlyEnabled, String keyword) {        EquipmentLedgerQueryDTO query = new EquipmentLedgerQueryDTO();
        query.setPageNum(pageNum);
        query.setPageSize(20);
        query.setOnlyEnabled(onlyEnabled);
        query.setKeyword(keyword);
        return query;
    }

    private void stubSelectPage(List<EquipmentLedger> rows) {
        // selectPage 是 BaseMapper 上的 default 方法（内部自己去调 selectList），
        // Mockito 不执行真实的默认实现 —— 不打桩它会返回 null，测试直接 NPE。
        when(baseMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<EquipmentLedger> page = invocation.getArgument(0);
            page.setRecords(rows);
            page.setTotal(rows.size());
            return page;
        });
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<QueryWrapper<EquipmentLedger>> wrapperCaptor() {
        return ArgumentCaptor.forClass(QueryWrapper.class);
    }
}
