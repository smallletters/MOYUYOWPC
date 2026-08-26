import zipfile
import xml.etree.ElementTree as ET

NS = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'

with zipfile.ZipFile('MOYUYO会员积分规则说明.docx') as z:
    xml = z.read('word/document.xml').decode('utf-8')

root = ET.fromstring(xml)
body = root.find(f'{NS}body')

for el in body.iter():
    tag = el.tag.replace(NS, '')
    if tag == 'p':
        text = ''.join(t.text or '' for t in el.iter(f'{NS}t'))
        if text.strip():
            print(text)
    elif tag == 'tr':
        cells = []
        for tc in el.findall(f'{NS}tc'):
            cell_text = ''.join(t.text or '' for t in tc.iter(f'{NS}t'))
            cells.append(cell_text.strip())
        print(' | '.join(cells))
        print('---ROW---')