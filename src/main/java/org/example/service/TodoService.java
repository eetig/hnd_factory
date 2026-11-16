package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.TodoSaveDTO;
import org.example.dto.TodoVO;
import org.example.entity.TodoItem;
import org.example.exception.BusinessException;
import org.example.mapper.TodoItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 待办（2026-11-16）。
 *
 * <h2>排序口径</h2>
 * 未完成：按截止日升序，没定日期的排最后（它们不该插队到「今天到期」前面）；
 * 已完成：按勾完时间倒序，刚做完的在最上面。
 *
 * <p>排序放在后端而不是手机端，是为了「手机上、以后可能的电脑端」看到同一个顺序。
 */
@Service
public class TodoService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private TodoItemMapper todoItemMapper;

    /** scope: open（未完成）/ done（已完成） */
    public List<TodoVO> list(String scope) {
        QueryWrapper<TodoItem> wrapper = new QueryWrapper<>();
        boolean done = "done".equalsIgnoreCase(scope);
        wrapper.eq("done", done ? 1 : 0);
        if (done) {
            wrapper.orderByDesc("done_at").orderByDesc("id");
        } else {
            // 没定日期的排最后：MySQL 里 NULL 在 ASC 时本来就排最前，所以显式排一次
            wrapper.orderByAsc("due_date").orderByDesc("id");
        }
        List<TodoItem> rows = todoItemMapper.selectList(wrapper);
        if (!done) {
            // 把「没日期」的挪到最后，同时保持其余按到期日升序
            rows = rows.stream()
                    .sorted((a, b) -> {
                        boolean an = a.getDueDate() == null;
                        boolean bn = b.getDueDate() == null;
                        if (an != bn) {
                            return an ? 1 : -1;
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());
        }
        return rows.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public TodoVO create(TodoSaveDTO dto, Long userId) {
        if (dto == null || isBlank(dto.getTitle())) {
            throw new BusinessException("先写一句要办什么");
        }
        TodoItem row = new TodoItem();
        row.setTitle(dto.getTitle().trim());
        row.setNote(trim(dto.getNote()));
        row.setDueDate(parseDate(dto.getDueDate()));
        row.setImages(writeImages(dto.getImages()));
        row.setDone(0);
        row.setCreatedBy(userId);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        todoItemMapper.insert(row);
        return toVO(row);
    }

    @Transactional
    public TodoVO update(Long id, TodoSaveDTO dto) {
        TodoItem row = require(id);
        if (dto == null || isBlank(dto.getTitle())) {
            throw new BusinessException("先写一句要办什么");
        }
        row.setTitle(dto.getTitle().trim());
        row.setNote(trim(dto.getNote()));
        row.setDueDate(parseDate(dto.getDueDate()));
        row.setImages(writeImages(dto.getImages()));
        row.setUpdatedAt(LocalDateTime.now());
        todoItemMapper.updateById(row);
        return toVO(row);
    }

    /**
     * 勾选 / 取消勾选。
     *
     * <p>勾完不清空截止日也不搬走数据，只翻 done 与 doneAt —— 「已完成」是同一批数据的另一个视角，
     * 取消勾选就能原样回到清单里。
     */
    @Transactional
    public TodoVO toggleDone(Long id, boolean done) {
        TodoItem row = require(id);
        row.setDone(done ? 1 : 0);
        row.setDoneAt(done ? LocalDateTime.now() : null);
        row.setUpdatedAt(LocalDateTime.now());
        todoItemMapper.updateById(row);
        return toVO(row);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        todoItemMapper.deleteById(id);
    }

    private TodoItem require(Long id) {
        TodoItem row = id == null ? null : todoItemMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("这条待办已经不在了，刷新看看");
        }
        return row;
    }

    private TodoVO toVO(TodoItem row) {
        TodoVO vo = new TodoVO();
        vo.setId(row.getId());
        vo.setTitle(row.getTitle());
        vo.setNote(row.getNote());
        vo.setDueDate(row.getDueDate() == null ? null : row.getDueDate().format(DATE));
        vo.setDone(row.getDone() != null && row.getDone() == 1);
        vo.setDoneAt(row.getDoneAt() == null ? null : row.getDoneAt().format(DATETIME));
        vo.setImages(readImages(row.getImages()));
        vo.setCreatedBy(row.getCreatedBy() == null ? null : String.valueOf(row.getCreatedBy()));
        vo.setCreatedAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(DATETIME));
        return vo;
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> cleaned = images.stream()
                .filter(path -> !isBlank(path))
                .map(String::trim)
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw new BusinessException("附图存不下来：" + e.getMessage());
        }
    }

    private List<String> readImages(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new BusinessException("日期格式不对，要 yyyy-MM-dd");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
