package cat.narezany.tokkiyou;
/** Native comment-copy formatter: preserve the body, omit author metadata. */
public final class CommentClipboard {
    private CommentClipboard() {}
    public static android.content.ClipData LIZ(String author,String body,java.util.List mentions) {
        return android.content.ClipData.newPlainText("TikTok comment",body==null ? "" : body);
    }
}
