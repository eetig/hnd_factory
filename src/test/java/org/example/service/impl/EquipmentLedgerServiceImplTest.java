package org.example.service.impl;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.dto.EquipmentLedgerVO;
import org.example.dto.EquipmentSaveDTO;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    }

    private static EquipmentSaveDTO draft() {
        EquipmentSaveDTO dto = new EquipmentSaveDTO();
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
        // 反向：底图坐标不该被开放编辑（它是按图量的，手填必然错位）
        assertNull(EquipmentLedger.class.getDeclaredField("imageBounds").getAnnotation(TableField.class),
                "imageBounds 不该带可为空更新注解");
    }

    @Test
    @DisplayName("停用/启用：只动 enabled 一列")
    void togglesEnabled() {
        service.setEnabled(89L, false);

        ArgumentCaptor<EquipmentLedger> captor = ArgumentCaptor.forClass(EquipmentLedger.class);
        verify(baseMapper).updateById(captor.capture());
        EquipmentLedger row = captor.getValue();
        assertEquals(89L, row.getId());
        assertEquals(0, row.getEnabled());
        assertNull(row.getEquipmentName(), "只该带 enabled 一列，别的字段保持 null 不被写出");
    }

    @Test
    @DisplayName("列表：停用的行也要返回（维护页要能重新启用）")
    void listAllKeepsDisabledRows() {
        EquipmentLedger disabled = new EquipmentLedger();
        disabled.setId(9L);
        disabled.setEquipmentName("停用的罐");
        disabled.setEnabled(0);
        when(baseMapper.selectList(any())).thenReturn(List.of(disabled));

        List<EquipmentLedgerVO> rows = service.listAll();

        assertEquals(1, rows.size());
        // 与 search / listVessels 不同：那两个要排掉停用的行，这个不能排
        verify(baseMapper).selectList(isNull());
    }
}
