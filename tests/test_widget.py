import pathlib, re, sys, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from margyt import axml, manifest, widget


class WidgetTests(unittest.TestCase):
    def test_the_two_files_read_back_as_what_was_meant(self):
        layout = axml.Axml.parse(widget.layout_xml())
        view = layout.elements("ImageView")[0]
        self.assertEqual(layout.attr(view, "id").data, 0x01020000)
        self.assertEqual(layout.attr(view, "layout_width").data, 0xFFFFFFFF)
        info = axml.Axml.parse(widget.info_xml())
        said = info.elements("appwidget-provider")[0]
        self.assertEqual(info.attr(said, "initialLayout").data, widget.LAYOUT_ID)
        self.assertEqual(info.attr(said, "updatePeriodMillis").data, 0)
        # an attribute's name has to sit where the map says its id is
        for document in (layout, info):
            for at, number in enumerate(document.resource_map):
                self.assertEqual(axml.ATTR_IDS[document.pool.get(at)], number)

    def test_the_layout_number_is_the_one_the_class_uses(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/CatWidget.java").read_text(encoding="utf-8")
        written = int(re.search(r"static final int LAYOUT = (0x[0-9A-Fa-f]+);", source).group(1), 16)
        self.assertEqual(written, widget.LAYOUT_ID)

    def test_the_manifest_gains_a_receiver_the_launcher_can_find(self):
        document = axml.Axml(axml.StringPool([], axml.StringPool.UTF8, b"", []), [], [])
        root = document.make_element("manifest")
        app = document.make_element("application")
        document.nodes = [root, app, document.close_element(app), document.close_element(root)]
        document.pending = []
        manifest.add_widget(document, "cat.narezany.margyt.CatWidget", widget.XML_ID)
        again = axml.Axml.parse(document.build())
        receiver = again.elements("receiver")[0]
        self.assertEqual(again.attr_string(receiver, "name"), "cat.narezany.margyt.CatWidget")
        self.assertEqual(again.attr(receiver, "exported").data, 0xFFFFFFFF)
        self.assertEqual(again.attr_string(again.elements("action")[0], "name"),
                         "android.appwidget.action.APPWIDGET_UPDATE")
        meta = again.elements("meta-data")[0]
        self.assertEqual(again.attr_string(meta, "name"), "android.appwidget.provider")
        self.assertEqual(again.attr(meta, "resource").data, widget.XML_ID)


if __name__ == "__main__":
    unittest.main()
