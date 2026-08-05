# -*- coding: utf-8 -*-
import sys
import re
import requests
from urllib.parse import quote, unquote
sys.path.append('..')
from base.spider import Spider

class Spider(Spider):
    def init(self, extend=""):
        self.host = "https://hubserieshd.com"
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer": self.host + "/",
            "Origin": self.host
        }

    def getName(self):
        return "HubSeriesHD"

    def isVideoFormat(self, url):
        return bool(re.search(r'\.(m3u8|mp4|flv|avi|mkv|mov|ts)(\?|$)|/get_file/', url or "", re.I))

    def manualVideoCheck(self):
        return False

    def homeContent(self, filter):
        return {
            "class": [
                {"type_id": "country/korea", "type_name": "ซีรีส์เกาหลี"},
                {"type_id": "category/series", "type_name": "ซีรีส์"},
                {"type_id": "category/movies", "type_name": "ภาพยนตร์"},
                {"type_id": "country/china", "type_name": "ซีรีส์จีน"},
                {"type_id": "country/japan", "type_name": "ซีรีส์ญี่ปุ่น"}
            ]
        }

    def homeVideoContent(self):
        return {"list": self.parseList(self.get(self.host + "/"))}

    def categoryContent(self, tid, pg, filter, extend):
        path = str(tid or "country/korea").strip("/")
        if str(pg) == "1":
            url = f"{self.host}/{path}/"
        else:
            url = f"{self.host}/{path}/page/{pg}/"
        
        html = self.get(url)
        return {
            "page": int(pg),
            "pagecount": 999,
            "limit": 20,
            "total": 99999,
            "list": self.parseList(html)
        }

    def detailContent(self, ids):
        vid = ids[0]
        html = self.get(vid)
        
        name = self.clean(self.match(html, r'<h1[^>]*class=["\'][^"\']*entry-title[^"\']*["\'][^>]*>(.*?)</h1>') or self.match(html, r'<meta property="og:title" content="(.*?)"'))
        pic = self.fix(self.match(html, r'<meta property="og:image" content="(.*?)"') or self.match(html, r'<img[^>]+src=["\']([^"\']+)["\'][^>]*class=["\'][^"\']*poster[^"\']*'))
        desc = self.clean(self.match(html, r'<div[^>]+class=["\'][^"\']*entry-content[^"\']*["\'][^>]*>(.*?)</div>') or self.match(html, r'<meta property="og:description" content="(.*?)"'))
        remarks = self.clean(self.match(html, r'<span[^>]+class=["\'][^"\']*quality[^"\']*["\'][^>]*>(.*?)</span>'))
        
        play_from = []
        play_url = []
        
        # ดึงลิงก์ตัวเล่น/ตอนต่างๆ จาก iframe หรือแผ่นเล่นในหน้าเว็บ
        episodes = []
        iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html, re.I)
        for idx, iframe_url in enumerate(iframes, 1):
            iframe_url = self.fix(iframe_url)
            if "facebook" not in iframe_url and "twitter" not in iframe_url:
                episodes.append(f"ตอนที่ {idx}${iframe_url}")
        
        if not episodes:
            # สำรอง: ใช้ลิงก์หน้าหลักเป็นลิงก์เล่น
            episodes.append(f"เล่นMain${vid}")

        if episodes:
            play_from.append("HubSeriesHD")
            play_url.append("#".join(episodes))

        return {
            "list": [{
                "vod_id": vid,
                "vod_name": name,
                "vod_pic": self.img(pic),
                "vod_remarks": remarks,
                "type_name": "",
                "vod_year": "",
                "vod_area": "Korea",
                "vod_lang": "",
                "vod_actor": "",
                "vod_director": "",
                "vod_content": desc,
                "vod_play_from": "$$$".join(play_from),
                "vod_play_url": "$$$".join(play_url)
            }]
        }

    def searchContent(self, key, quick, pg="1"):
        q = quote(key)
        if str(pg) == "1":
            url = f"{self.host}/?s={q}"
        else:
            url = f"{self.host}/page/{pg}/?s={q}"
            
        html = self.get(url)
        return {"list": self.parseList(html), "page": int(pg)}

    def playerContent(self, flag, id, vipFlags):
        url = id
        return {
            "parse": 1 if not self.isVideoFormat(url) else 0,
            "playUrl": "",
            "url": url,
            "header": self.headers
        }

    def localProxy(self, param):
        url = ""
        for k in ["url", "img", "pic"]:
            v = param.get(k, "")
            if isinstance(v, list):
                v = v[0] if len(v) > 0 else ""
            if v:
                url = v
                break
        url = unquote(url or "")
        if not url:
            return [404, "text/plain", "", ""]
        try:
            h = dict(self.headers)
            h.update({
                "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                "Referer": self.host + "/"
            })
            r = requests.get(url, headers=h, timeout=20, verify=False)
            ct = r.headers.get("Content-Type", "")
            if r.status_code == 200 and r.content:
                return [200, ct or "image/jpeg", r.content, ""]
        except Exception:
            pass
        return [404, "text/plain", "", ""]

    def destroy(self):
        return "Destroyed"

    def get(self, url):
        try:
            r = requests.get(url, headers=self.headers, timeout=15, verify=False)
            r.encoding = r.apparent_encoding or "utf-8"
            return r.text
        except Exception:
            return ""

    def match(self, text, rule):
        m = re.search(rule, text or "", re.S)
        return m.group(1) if m else ""

    def clean(self, text):
        return re.sub(r"\s+", " ", re.sub(r"<.*?>", "", text or "").replace("&nbsp;", " ")).strip()

    def fix(self, url):
        if not url:
            return ""
        if url.startswith("//"):
            return "https:" + url
        if url.startswith("/"):
            return self.host + url
        return url

    def img(self, url):
        url = self.fix(url)
        if not url:
            return ""
        try:
            return self.getProxyUrl() + "&url=" + quote(url, safe="")
        except Exception:
            return url

    def parseList(self, html):
        res = []
        seen = set()
        
        # Regex สำหรับดึงบทความ/ซีรีส์จากหน้าเว็บ WordPress Standard
        pattern = r'<article[^>]*>.*?<a\s+href=["\']([^"\']+)["\'][^>]*title=["\']([^"\']+)["\'].*?<img[^>]+(?:data-src|src)=["\']([^"\']+)["\'].*?</article>'
        matches = re.finditer(pattern, html or "", re.S)
        
        for m in matches:
            url = m.group(1)
            name = self.clean(m.group(2))
            pic = m.group(3)
            
            if url in seen:
                continue
            seen.add(url)
            
            res.append({
                "vod_id": url,
                "vod_name": name,
                "vod_pic": self.img(pic),
                "vod_remarks": "HD"
            })
            
        # สำรอง: กรณีที่โครงสร้าง HTML ไม่ตรงกับ article tag ข้างต้น
        if not res:
            alt_pattern = r'<a\s+href=["\'](https?://hubserieshd\.com/[^"\']+)["\'][^>]*>(?:(?!</a>).)*?alt=["\']([^"\']+)["\'].*?src=["\']([^"\']+)["\']'
            for m in re.finditer(alt_pattern, html or "", re.S):
                url = m.group(1)
                name = self.clean(m.group(2))
                pic = m.group(3)
                if url in seen or "/category/" in url or "/country/" in url:
                    continue
                seen.add(url)
                res.append({
                    "vod_id": url,
                    "vod_name": name,
                    "vod_pic": self.img(pic),
                    "vod_remarks": ""
                })

        return res
