package org.example.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 图片 URL 契约。
 *
 * <p>这三件事原先由 5 份逐字节相同的私有方法各自保证（谁都没测）。现在只有一处实现，
 * 口径在这里钉住：
 * <ul>
 *   <li>{@code image.base-url} 留空 = 同源部署，前端拿到 {@code /files/xxx} 相对路径（契约 2.3）；</li>
 *   <li>原图走 {@code /files/}、缩略图走 {@code /thumbs/}，两者不可互换；</li>
 *   <li>文件名为空时返回 null，而不是拼出 {@code .../files/null} 让前端去下载一个 404。</li>
 * </ul>
 */
class ImageUrlBuilderTest {

    @Test
    @DisplayName("同源部署（base-url 留空）：相对路径，前端的 baseURL 会自己补")
    void buildsRelativeUrlWhenBaseIsBlank() {
        ImageUrlBuilder builder = new ImageUrlBuilder("");
        assertEquals("/files/abc.jpg", builder.urlOf("abc.jpg"));
        assertEquals("/thumbs/abc.jpg", builder.thumbUrlOf("abc.jpg"));
    }

    @Test
    @DisplayName("配了基础路径：直接加前缀（预签名 URL 会让浏览器缓存全失效，故不用）")
    void buildsAbsoluteUrlWhenBaseIsSet() {
        ImageUrlBuilder builder = new ImageUrlBuilder("https://cdn.example.com");
        assertEquals("https://cdn.example.com/files/abc.jpg", builder.urlOf("abc.jpg"));
        assertEquals("https://cdn.example.com/thumbs/abc.jpg", builder.thumbUrlOf("abc.jpg"));
    }

    @Test
    @DisplayName("原图与缩略图是两个前缀，不能混用")
    void originalAndThumbUseDifferentPrefixes() {
        ImageUrlBuilder builder = new ImageUrlBuilder("https://cdn.example.com");
        assertNotEquals(builder.urlOf("abc.jpg"), builder.thumbUrlOf("abc.jpg"));
    }

    @Test
    @DisplayName("没有图（null/空/空白）返回 null，不拼出 /files/null")
    void returnsNullForMissingFileName() {
        ImageUrlBuilder builder = new ImageUrlBuilder("https://cdn.example.com");
        assertNull(builder.urlOf(null));
        assertNull(builder.urlOf(""));
        assertNull(builder.urlOf("   "));
        assertNull(builder.thumbUrlOf(null));
        assertNull(builder.thumbUrlOf(""));
        assertNull(builder.thumbUrlOf("   "));
    }
}
