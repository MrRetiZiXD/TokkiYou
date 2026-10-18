package cat.narezany.tokkiyou;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.comment.model.CommentImageStruct;
import com.ss.android.ugc.aweme.comment.model.CommentStickerStruct;
import com.ss.android.ugc.aweme.im.common.model.StickerItem;

import kotlin.jvm.functions.Function0;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stamp on an image saved out of a comment.
 *
 * A photo post keeps a clean copy beside the stamped one and the mod hands
 * back the clean one. A comment image has no such pair -- and for a while it
 * looked like there was nothing to take. The words are nowhere in the apk, in
 * any language, and the saved file is a standard CDN width with a colour
 * profile and no exif, so the stamp is not drawn on the phone: a server makes
 * it.
 *
 * What settled it was reading both addresses off a running app. The same image
 * arrives twice, under one uri, differing by one word:
 *
 *     shown:  .../<hash>~tplv-jj85edgx6n-image-medium.image
 *     saved:  .../<hash>~tplv-jj85edgx6n-image-origin.image
 *
 * The template in the path is what tells the server which rendering to make,
 * and `origin` is the one it stamps. `medium` is the one on screen, which is
 * why the picture in the comment has no stamp while the file on disk does --
 * at the same 640x480, so this is not a matter of size.
 *
 * So the fix asks for the other rendering: the address is copied with the
 * template swapped, and everything else about it left alone. When the switch
 * is off, or when the address does not look like this, what comes back is
 * exactly what TikTok asked for.
 */
public final class Comments {
    static void reloadSettings(){copyEnabled=null;}

    private Comments() {}
    public static void mark(View cell) {
        if(cell!=null)cell.setTag(0x5454434D,Boolean.TRUE);
    }

    /** Only lookups inside TikTok's native comment cell are routed here. */
    public static View findViewById(View cell,int id) {
        mark(cell);
        View result=cell.findViewById(id);
        if(result!=null)result.setTag(0x5454434D,Boolean.TRUE);
        return result;
    }

    /** Whether a long press on a comment offers local text actions. */
    public static final String KEY_COPY = "comment_copy";
    private static volatile Boolean copyEnabled;

    /** The rendering the server stamps, and the one it does not. */
    private static final String STAMPED = "-image-origin.";
    private static final String PLAIN = "-image-medium.";

    private static final Set<String> told = new HashSet<String>();

    public static boolean isCopyEnabled() {
        Boolean known = copyEnabled;
        if (known != null) return known.booleanValue();
        try {
            Context context = TokkiYou.context();
            if (context == null) return true;
            boolean on = context.getSharedPreferences(TokkiYou.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_COPY, true);
            copyEnabled = Boolean.valueOf(on);
            return on;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setCopyEnabled(boolean enabled) {
        copyEnabled = Boolean.valueOf(enabled);
        try {
            Context context = TokkiYou.context();
            if (context != null) context.getSharedPreferences(TokkiYou.PREFS,
                    Context.MODE_PRIVATE).edit().putBoolean(KEY_COPY, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * A sticker in a comment, tapped.
     *
     * In a conversation the tap goes through an interface with a real name and
     * the mod simply hears it. In the comments it goes to a static on a class
     * whose name changes every release -- so the build finds that class by
     * what the method takes rather than by what it is called, and writes down
     * where it landed. The call is handed straight back, so the panel TikTok
     * opens on a tap still opens.
     */
    public static void stickerTapped(View view, CommentStickerStruct sticker,
                                     boolean flag, String from,
                                     Map extras, String where) {
        try {
            // a tap opens the sheet, and the button goes in the sheet
            if (sticker != null && Stickers.isEnabled()) Stickers.sheet(sticker);
        } catch (Throwable error) {
            Diary.note("comment sticker: " + error);
        }
        onwards(view, sticker, flag, from, extras, where);
    }

    /** Back to TikTok's own, wherever this release keeps it. */
    private static void onwards(Object... args) {
        try {
            Class<?> owner = Class.forName(Anchors.COMMENT_STICKER_TAPPED);
            for (java.lang.reflect.Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(Anchors.COMMENT_STICKER_TAPPED_METHOD)) continue;
                if (method.getParameterTypes().length != args.length) continue;
                method.setAccessible(true);
                method.invoke(null, args);
                return;
            }
            Diary.note("comment sticker tap: nothing to hand it back to");
        } catch (Throwable error) {
            Diary.note("comment sticker tap: " + error);
        }
    }

    /**
     * The sheet that opens on a comment's sticker -- Share, Save, Use.
     *
     * This is the one, and the earlier attempts were not. The static the build
     * found first turned out to be the Save button itself, which is why the
     * offer only ever appeared once something in the sheet had been pressed.
     * This is the method that builds the sheet, it is handed the sticker it is
     * about, and it runs the moment the sheet opens.
     *
     * The sticker arrives as TikTok's own `StickerItem` -- the same model a
     * sticker in a conversation is -- so saving it needs nothing new.
     *
     * The sheet is handed its call back first and the offer is made after, so
     * that the offer's window is created second and lands on top of it.
     */
    public static void stickerSheet(Object helper, String where, StickerItem sticker,
                                    View view, boolean saved, String from, Map extras,
                                    Function0 onShare, Function0 onSave, Function0 onUse) {
        try {
            java.lang.reflect.Method their = null;
            for (java.lang.reflect.Method method : helper.getClass().getMethods()) {
                if (method.getName().equals(Anchors.COMMENT_STICKER_SHEET_METHOD)
                        && method.getParameterTypes().length == 9) {
                    their = method;
                    break;
                }
            }
            if (their != null) {
                their.setAccessible(true);
                their.invoke(helper, where, sticker, view, Boolean.valueOf(saved),
                        from, extras, onShare, onSave, onUse);
            } else {
                Diary.note("comment sticker sheet: nothing to hand it back to");
            }
        } catch (Throwable error) {
            Diary.note("comment sticker sheet: " + error);
        }

        try {
            if (sticker != null && Stickers.isEnabled()) Stickers.sheet(sticker);
        } catch (Throwable error) {
            Diary.note("comment sticker: " + error);
        }
    }

    /** The sticker a comment is carrying, remembered for the view that shows it. */
    public static CommentStickerStruct getStickerStruct(Object comment) {
        CommentStickerStruct sticker = null;
        try {
            java.lang.reflect.Method method =
                    comment.getClass().getMethod("getStickerStruct");
            method.setAccessible(true);
            sticker = (CommentStickerStruct) method.invoke(comment);
        } catch (Throwable error) {
            Diary.note("comment sticker: " + error);
            return null;
        }
        if (sticker != null) lastBound = sticker;
        return sticker;
    }

    /** The sticker most recently bound, for the view about to be wrapped. */
    private static volatile CommentStickerStruct lastBound;

    /**
     * A long press on a sticker in a comment.
     *
     * TikTok sets its own listener on that view, so there is a gesture already
     * and the mod does not need to invent one: this wraps what was about to be
     * set. The press still does what TikTok made it do -- our listener says so
     * by handing the event on and returning what TikTok's returns -- and the
     * offer to save appears alongside.
     *
     * Which sticker it is comes from the listener itself where it can be found
     * there, and from the last one bound where it cannot: the view is wrapped
     * in the same breath as the comment is read, so the two go together.
     */
    public static void setOnLongClickListener(View view,
                                              final View.OnLongClickListener theirs) {
        if (view == null) return;
        view.setTag(0x5454434D,Boolean.TRUE); // comment row/sticker; never dim it
        // Wrapped without asking what the view is showing, because at the
        // moment a listener is set it is usually showing nothing at all: these
        // are recycled list rows, built once and filled over and over. Asking
        // then found no sticker and left the row unwrapped for good, so the
        // offer only ever turned up later, by way of the menu the press opens.
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View pressed) {
                // This method is installed in two deliberately anchored places:
                // a sticker and the complete comment row.  The latter has
                // readable text when the press occurs; a sticker does not.
                // Decide at the event, not when a recycled row is bound.
                String comment = isCopyEnabled() ? CommentText.from(theirs) : null;
                if (isCopyEnabled() && comment == null) comment = visibleComment(pressed);
                if (!TextUtils.isEmpty(comment)
                        && showCommentActions(pressed, comment, theirs)) return true;
                try {
                    if (Stickers.isEnabled()) {
                        CommentStickerStruct sticker =
                                hunt(theirs, new HashSet<Object>(), 0);
                        if (sticker == null) sticker = lastBound;
                        if (sticker != null) Stickers.seen(sticker);
                    }
                } catch (Throwable error) {
                    Diary.note("comment sticker: " + error);
                }
                return theirs != null && theirs.onLongClick(pressed);
            }
        });
    }

    private static boolean showCommentActions(final View pressed, final String comment,
                                              final View.OnLongClickListener theirs) {
        return showCommentActions(pressed.getContext(),comment,()->nativeLongPress(theirs,pressed));
    }
    private static final ThreadLocal<Boolean> nativeMenu=new ThreadLocal<Boolean>();
    /** The shared native text-comment menu entry, including gesture-driven rows. */
    public static boolean openNativeActions(Object cell) {
        // The native fragment now supplies the action. Never intercept its opening.
        return false;
    }
    /** Bind the actual body after recycling; retain native taps on reactions and authors. */
    public static void bindCell(Object cell){
        try{View root=(View)cell.getClass().getField("itemView").get(cell);if(root==null)return;mark(root);if(!isCopyEnabled())return;
            String body=CommentText.from(cell);if(TextUtils.isEmpty(body))return;
            View.OnLongClickListener listener=v->{if(openNativeActions(cell))return true;nativeMenu.set(Boolean.TRUE);try{cell.getClass().getMethod("k8").invoke(cell);return true;}catch(Throwable ignored){return false;}finally{nativeMenu.remove();}};root.setOnLongClickListener(listener);
            bindBody(root,body,listener,0,new int[]{128});
        }catch(Throwable error){Diary.note("comment body actions: "+error);}
    }
    private static void bindBody(View view,String body,View.OnLongClickListener listener,int depth,int[] remaining){
        if(view==null||depth>12||remaining[0]--<=0)return;
        if(view instanceof TextView&&((TextView)view).getText()!=null&&body.contentEquals(((TextView)view).getText()))view.setOnLongClickListener(listener);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)bindBody(group.getChildAt(i),body,listener,depth+1,remaining);}
    }
    private static boolean showCommentActions(Context context,String comment,Runnable nativeAction) {
        return false;
    }

    /** 47.2.41: append an ordinary 0oi8 action to the native long-press adapter.
     * Copy, report, favourites and sharing retain their native handlers. */
    public static List nativeItems(Object fragment,List items) {
        if(!isCopyEnabled() || items==null)return items;
        try {
            Object comment=NativeRead.field(fragment,"LLJJL");
            String body=NativeRead.string(NativeRead.get(comment,"getText"));
            if(TextUtils.isEmpty(body))return items;
            Context context=(Context)NativeRead.get(fragment,"getContext");
            if(context==null)return items;
            int after=items.size();Integer icon=null;
            for(int i=0;i<items.size();i++) {
                Object item=items.get(i);
                String key=NativeRead.string(NativeRead.field(item,"LIZIZ"));
                if("tokkiyou_select_comment".equals(key))return items;
                String label=NativeRead.string(NativeRead.field(item,"LIZLLL")).toLowerCase(java.util.Locale.ROOT);
                if(key.toLowerCase(java.util.Locale.ROOT).contains("copy") || label.contains("копир") || label.equals("copy")) {
                    after=i+1;Object art=NativeRead.field(item,"LIZJ");if(art instanceof Integer)icon=(Integer)art;
                }
            }
            Class<?> type=Class.forName("X.0oi8");
            Object action=type.getConstructor().newInstance();
            type.getField("LIZIZ").set(action,"tokkiyou_select_comment");
            type.getField("LIZLLL").set(action,Text.COMMENT_SELECT_COPY);
            type.getField("LIZJ").set(action,icon);
            type.getField("LJ").set(action,(Function0<Object>)()->{
                try{fragment.getClass().getMethod("dismissAllowingStateLoss").invoke(fragment);}catch(Throwable ignored){}
                selectComment(context,body);return null;
            });
            ArrayList result=new ArrayList(items);result.add(after,action);return result;
        }catch(Throwable error){Diary.note("native comment selection: "+error);return items;}
    }

    private static boolean nativeLongPress(View.OnLongClickListener theirs, View pressed) {
        nativeMenu.set(Boolean.TRUE);
        try {
            return theirs != null && theirs.onLongClick(pressed);
        } catch (Throwable error) {
            Diary.note("comment long press: " + error);
            return false;
        } finally {
            nativeMenu.remove();
        }
    }

    /** A fallback is allowed only for explicitly named comment-body widgets. */
    private static String visibleComment(View root) {
        if (root==null || root.getVisibility()!=View.VISIBLE) return null;
        if (root instanceof TextView) {
            try {
                String id=Names.of(root);
                if (id.equals("comment_text") || id.equals("comment_content") || id.equals("comment_body")) {
                    CharSequence body=((TextView)root).getText();
                    if (body!=null && body.toString().trim().length()>0) return body.toString();
                }
            } catch (Throwable ignored) { }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) {
                String body=visibleComment(group.getChildAt(i));
                if (body!=null) return body;
            }
        }
        return null;
    }

    private static void copyComment(Context context, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                    Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText("TikTok comment", text));
            Screen.say(Text.COMMENT_COPIED);
        } catch (Throwable error) {
            Diary.note("comment copy: " + error);
        }
    }

    /** A selectable native text view leaves Android's usual selection handles intact. */
    private static void selectComment(Context context, String text) {
        try {
            Skin skin=Skin.remembered(context);
            TextView body=new TextView(context);body.setText(text);body.setTextColor(skin.text);
            body.setTextSize(16);body.setTextIsSelectable(true);body.setFocusable(true);body.setFocusableInTouchMode(true);
            body.setLineSpacing(0,1.12f);Fonts.apply(body);
            int pad=(int)(context.getResources().getDisplayMetrics().density*8f+.5f);
            body.setPadding(pad,pad,pad,pad);
            Panel panel=Panel.with(context,skin,Text.COMMENT_SELECT);panel.view(body);
            panel.primaryKeepOpen(Text.COMMENT_COPY_SELECTED,()->{
                String selected=CommentSelection.part(text,body.getSelectionStart(),body.getSelectionEnd());
                if(selected==null){Screen.say(Text.COMMENT_SELECT_HINT);return;}
                copyComment(context,selected);panel.close();
            });
            panel.quiet(Text.CLOSE,null);panel.show();
        } catch (Throwable error) {
            Diary.note("comment selection: " + error);
        }
    }

    /** A comment's sticker, anywhere inside the object holding the listener. */
    private static CommentStickerStruct hunt(Object thing, Set<Object> seen, int depth) {
        if (thing == null || depth > 3) return null;
        if (thing instanceof CommentStickerStruct) return (CommentStickerStruct) thing;
        Class<?> type = thing.getClass();
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("android.")) return null;
        if (!seen.add(thing)) return null;
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    CommentStickerStruct found = hunt(field.get(thing), seen, depth + 1);
                    if (found != null) return found;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    public static UrlModel getCropUrl(CommentImageStruct image) {
        return image == null ? null : image.getCropUrl();
    }

    public static UrlModel getOriginUrl(CommentImageStruct image) {
        if (image == null) return null;
        UrlModel origin = image.getOriginUrl();
        if (origin == null) return origin;
        try {
            List urls = origin.getUrlList();
            if (urls == null || urls.isEmpty()) return origin;

            List plain = new ArrayList(urls.size());
            boolean swapped = false;
            for (Object entry : urls) {
                String url = String.valueOf(entry);
                String other = url.replace(STAMPED, PLAIN);
                if (!other.equals(url)) swapped = true;
                plain.add(other);
            }
            if (!swapped) return origin;

            // a copy rather than the app's own object: the model is shared
            // with whatever else is holding this comment, and the stamped
            // address is still the right answer for everyone who did not ask
            // through here
            UrlModel clean = new UrlModel();
            clean.setUrlList(plain);
            clean.setUri(origin.getUri());
            say(origin.getUri());
            return clean;
        } catch (Throwable error) {
            Diary.note("comment image: " + error);
            return origin;
        }
    }

    /** Once per image: a diary full of the same line helps nobody. */
    private static void say(String uri) {
        synchronized (told) {
            if (told.size() > 40 || !told.add(String.valueOf(uri))) return;
        }
        Diary.note("comment image: asked for the unstamped rendering");
    }
}
