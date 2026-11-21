package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.InspectRecordSaveDTO;
import org.example.dto.InspectRecordVO;
import org.example.dto.InspectTemplateSaveDTO;
import org.example.dto.InspectTemplateVO;
import org.example.entity.InspectRecord;
import org.example.entity.InspectTemplate;
import org.example.entity.SysUser;
import org.example.exception.BusinessException;
import org.example.mapper.InspectRecordMapper;
import org.example.mapper.InspectTemplateMapper;
import org.example.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 巡检（2026-11-19 建立，2026-11-21 变更-255 改成「小作文」模型）。
 *
 * <p>两个概念：清单（一整篇要照着看的正文）和记录（某天某人照着看了一遍）。
 *
 * <p>为什么从「逐项点正常/异常」改成整篇正文：
 * <ol>
 *   <li>用户的检查内容本来就写好了是一段话，拆成 300 个选项只是把同一件事做两遍；</li>
 *   <li>现场要的是「对着念一遍、确认没漏」，不是逐条打勾；</li>
 *   <li>记录只留人/时间/哪篇/备注/照片，够追溯，也不需要人去统计异常数。</li>
 * </ol>
 *
 * <p>两个仍然保留的机制：正文只存一段文本（换行即分段，不做富文本）；
 * clientUuid 幂等（泵房没信号时先存手机，联网重传只落一行）。
 */
@Service
public class InspectService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 正文上限：一篇检查单写不到 2 万字，超了多半是粘错了东西 */
    private static final int MAX_BODY = 20000;

    /** 一次巡检最多 9 张照片（手机端九宫格） */
    private static final int MAX_PHOTOS = 9;

    @Autowired
    private InspectTemplateMapper templateMapper;

    @Autowired
    private InspectRecordMapper recordMapper;

    @Autowired
    private SysUserMapper userMapper;

    // ==================== 清单 ====================

    public List<InspectTemplateVO> listTemplates() {
        List<InspectTemplate> rows = templateMapper.selectList(
                new QueryWrapper<InspectTemplate>().orderByDesc("updated_at").orderByDesc("id"));
        List<InspectTemplateVO> out = new ArrayList<>();
        for (InspectTemplate row : rows) {
            out.add(toTemplateVO(row));
        }
        return out;
    }

    public InspectTemplateVO getTemplate(Long id) {
        return toTemplateVO(requireTemplate(id));
    }

    @Transactional
    public InspectTemplateVO createTemplate(InspectTemplateSaveDTO dto, Long userId) {
        String name = requireName(dto);
        String body = requireBody(dto);
        LocalDateTime now = LocalDateTime.now();
        InspectTemplate row = new InspectTemplate();
        row.setName(name);
        row.setKind(kindOf(dto));
        row.setBody(body);
        row.setEnabled(1);
        row.setCreatedBy(userId);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        templateMapper.insert(row);
        return toTemplateVO(row);
    }

    @Transactional
    public InspectTemplateVO updateTemplate(Long id, InspectTemplateSaveDTO dto) {
        InspectTemplate row = requireTemplate(id);
        row.setName(requireName(dto));
        row.setKind(kindOf(dto));
        row.setBody(requireBody(dto));
        row.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(row);
        return toTemplateVO(row);
    }

    /**
     * 删清单。
     *
     * <p>记录里存的是提交那一刻的清单名与正文快照，删清单不会让历史记录变空 ——
     * 这也是不在记录上做外键级联的原因。
     */
    @Transactional
    public void deleteTemplate(Long id) {
        InspectTemplate row = requireTemplate(id);
        templateMapper.deleteById(row.getId());
    }

    // ==================== 记录 ====================

    @Transactional
    public InspectRecordVO submitRecord(InspectRecordSaveDTO dto, Long userId) {
        if (dto == null) {
            throw new BusinessException("没有收到巡检内容");
        }
        String uuid = trim(dto.getClientUuid());
        if (uuid.length() > 64) {
            throw new BusinessException("clientUuid 太长");
        }
        if (!uuid.isEmpty()) {
            InspectRecord exist = recordMapper.selectOne(new QueryWrapper<InspectRecord>()
                    .eq("client_uuid", uuid).last("limit 1"));
            if (exist != null) {
                // 离线重传：同一条只收一次，直接把原来那条吐回去，手机端会把队列清掉
                return toRecordVO(exist);
            }
        }

        InspectTemplate template = requireTemplate(dto.getTemplateId());
        LocalDateTime now = LocalDateTime.now();
        InspectRecord row = new InspectRecord();
        row.setClientUuid(uuid.isEmpty() ? null : uuid);
        row.setTemplateId(template.getId());
        row.setTemplateName(template.getName());
        row.setKind(template.getKind());
        row.setOperatorId(userId);
        row.setOperatorName(operatorName(userId));
        row.setStartedAt(parseTime(dto.getStartedAt(), now));
        row.setFinishedAt(parseTime(dto.getFinishedAt(), now));
        row.setNote(cut(dto.getNote(), 500));
        List<String> images = cleanImages(dto.getImages());
        row.setImages(images.isEmpty() ? null : writeJson(images));
        row.setCreatedAt(now);
        recordMapper.insert(row);
        return toRecordVO(row);
    }

    /** 最近若干条，手机端列表用；倒序，最新的在最上面 */
    public List<InspectRecordVO> listRecords(int limit) {
        int size = limit <= 0 ? 50 : Math.min(limit, 200);
        List<InspectRecord> rows = recordMapper.selectList(new QueryWrapper<InspectRecord>()
                .orderByDesc("finished_at").orderByDesc("id").last("limit " + size));
        List<InspectRecordVO> out = new ArrayList<>();
        for (InspectRecord row : rows) {
            out.add(toRecordVO(row));
        }
        return out;
    }

    public InspectRecordVO getRecord(Long id) {
        InspectRecord row = id == null ? null : recordMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("这条巡检记录找不到了，刷新看看");
        }
        return toRecordVO(row);
    }

    // ==================== 内部 ====================

    private InspectTemplate requireTemplate(Long id) {
        InspectTemplate row = id == null ? null : templateMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("这篇巡检清单已经不在了，刷新看看");
        }
        return row;
    }

    private String requireName(InspectTemplateSaveDTO dto) {
        String name = dto == null ? "" : trim(dto.getName());
        if (name.isEmpty()) {
            throw new BusinessException("给这篇清单起个名字吧");
        }
        if (name.length() > 80) {
            throw new BusinessException("名字太长了，80 个字以内");
        }
        return name;
    }

    /**
     * 正文的规矩：整段保存，只做「统一换行 + 去掉每行尾空白 + 去掉首尾空行」三件事。
     *
     * <p>不动行首缩进、不动空行数量 —— 现场贴出来是什么样子，手机上看就是什么样子。
     */
    private String requireBody(InspectTemplateSaveDTO dto) {
        String raw = dto == null || dto.getBody() == null ? "" : dto.getBody();
        String body = normalizeBody(raw);
        if (body.isEmpty()) {
            throw new BusinessException("正文还是空的，把要照着看的内容写进去");
        }
        if (body.length() > MAX_BODY) {
            throw new BusinessException("正文太长了，2 万字以内");
        }
        return body;
    }

    private String normalizeBody(String raw) {
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(s.length());
        for (String line : s.split("\n", -1)) {
            String t = line.replaceAll("[\\t\\x0B\\f ]+$", "");
            if (sb.length() > 0 || !t.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t);
            }
        }
        // 收尾时把结尾多出来的空行砍掉（上面的循环会保留中间空行）
        String out = sb.toString();
        while (out.endsWith("\n")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private String kindOf(InspectTemplateSaveDTO dto) {
        return cut(dto == null ? null : dto.getKind(), 20);
    }

    private List<String> cleanImages(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String url : raw) {
            String clean = trim(url);
            if (!clean.isEmpty()) {
                out.add(clean);
            }
            if (out.size() >= MAX_PHOTOS) {
                break;
            }
        }
        return out;
    }

    private String operatorName(Long userId) {
        if (userId == null) {
            return "未知";
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return "未知";
        }
        String real = trim(user.getRealName());
        return real.isEmpty() ? trim(user.getUsername()) : real;
    }

    private InspectTemplateVO toTemplateVO(InspectTemplate row) {
        String body = row.getBody() == null ? "" : row.getBody();
        InspectTemplateVO vo = new InspectTemplateVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setKind(row.getKind());
        vo.setBody(body);
        vo.setCharCount(countChars(body));
        vo.setUpdatedAt(row.getUpdatedAt() == null ? "" : DATETIME.format(row.getUpdatedAt()));
        return vo;
    }

    private InspectRecordVO toRecordVO(InspectRecord row) {
        List<String> images = readImages(row.getImages());
        InspectRecordVO vo = new InspectRecordVO();
        vo.setId(row.getId());
        vo.setClientUuid(row.getClientUuid());
        vo.setTemplateId(row.getTemplateId());
        vo.setTemplateName(row.getTemplateName());
        vo.setKind(row.getKind());
        vo.setOperatorName(row.getOperatorName());
        vo.setStartedAt(row.getStartedAt() == null ? "" : DATETIME.format(row.getStartedAt()));
        vo.setFinishedAt(row.getFinishedAt() == null ? "" : DATETIME.format(row.getFinishedAt()));
        vo.setNote(row.getNote());
        vo.setImages(images);
        vo.setPhotoCount(images.size());
        vo.setCreatedAt(row.getCreatedAt() == null ? "" : DATETIME.format(row.getCreatedAt()));
        return vo;
    }

    /** 字数按「非空白字符」算：现场关心的是写了多少内容，不是排版占了多少格 */
    private int countChars(String body) {
        int n = 0;
        for (int i = 0; i < body.length(); i++) {
            if (!Character.isWhitespace(body.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("保存巡检内容失败：" + e.getMessage());
        }
    }

    private List<String> readImages(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = JSON.readValue(raw, new TypeReference<List<String>>() {});
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 手机端传 yyyy-MM-dd HH:mm；也接受带秒的 ISO，统一减去秒再解析 */
    private LocalDateTime parseTime(String raw, LocalDateTime fallback) {
        String s = trim(raw).replace('T', ' ');
        if (s.isEmpty()) {
            return fallback;
        }
        if (s.length() > 16) {
            s = s.substring(0, 16);
        }
        try {
            return LocalDateTime.parse(s, DATETIME);
        } catch (Exception e) {
            throw new BusinessException("时间格式看不懂：" + raw);
        }
    }

    private String cut(String raw, int max) {
        String s = trim(raw);
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
