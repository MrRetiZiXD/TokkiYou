package cat.narezany.margyt;
/** UTF-16 selection, without splitting an emoji surrogate pair. */
final class CommentSelection {
 private CommentSelection(){}
 static String part(String text,int start,int end){
  if(text==null || start<0 || end<0)return null;
  int low=Math.min(start,end),high=Math.max(start,end);
  if(low==high || low>=text.length() || high>text.length())return null;
  if(low>0 && Character.isLowSurrogate(text.charAt(low)) && Character.isHighSurrogate(text.charAt(low-1)))low--;
  if(high<text.length() && Character.isLowSurrogate(text.charAt(high)) && Character.isHighSurrogate(text.charAt(high-1)))high++;
  return text.substring(low,high);
 }
}
