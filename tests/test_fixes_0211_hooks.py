"""Verify native heart variants, comment-cell marking and listener preservation."""
import pathlib,tempfile,unittest
from tokkiyou import dexpatch
class FixesHookTest(unittest.TestCase):
 def rewrite(self,code):
  with tempfile.TemporaryDirectory() as folder:
   path=pathlib.Path(folder)/'view.smali';path.write_text(code,encoding='utf-8')
   counts=dexpatch.rewrite_anchored(folder);models=dexpatch.rewrite_models(folder)
   output=path.read_text(encoding='utf-8')
   self.assertFalse(dexpatch.rewrite_anchored(folder))
   return output,counts,models
 def test_native_tux_variants(self):
  code='''.class public LX/0HCj;
.super Landroid/widget/FrameLayout;
invoke-virtual {v0, v1}, Lcom/bytedance/tux/icon/TuxIconView;->setIconRes(I)V
invoke-virtual {v0, v2}, Lcom/bytedance/tux/icon/TuxIconView;->setTintColor(I)V
'''
  out,counts,_=self.rewrite(code)
  self.assertIn('Lcat/narezany/tokkiyou/Heart;->setIconRes',out)
  self.assertIn('Lcat/narezany/tokkiyou/Heart;->setTintColor',out)
  self.assertEqual(2,sum(counts.values()))
  untouched,_,_=self.rewrite(code.replace('LX/0HCj;','LX/Other;'))
  self.assertNotIn('Lcat/narezany/tokkiyou/Heart;',untouched)
 def test_comments_marked_at_item_binding(self):
  code='''.class public Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;
.super Landroidx/recyclerview/widget/RecyclerView$ViewHolder;
iget-object v20, p0, Landroidx/recyclerview/widget/RecyclerView$ViewHolder;->itemView:Landroid/view/View;
'''
  out,counts,_=self.rewrite(code)
  self.assertIn('invoke-static/range {v20 .. v20}, Lcat/narezany/tokkiyou/Comments;->mark',out)
  self.assertEqual(1,counts['native comment cell protection'])
  out,counts,_=self.rewrite(code.replace('BaseCommentCell;','OtherCell;'))
  self.assertNotIn('Comments;->mark',out)
