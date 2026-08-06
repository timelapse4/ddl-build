# -*- coding: utf-8 -*-
import sys
import re
import requests
import json
from urllib.parse import quote, unquote
sys.path.append('..')
from base.spider import Spider

class Spider(Spider):
    def init(self, extend=""):
        self.host = "https://hubserieshd.com"
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
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
                {"type_id": "country/usa", "type_name": "ซีรีส์ฝรั่ง"},
                {"type_id": "country/china", "type_name": "ซีรีส์จีน"}
            ]
        }

    def homeVideoContent(self):
        return {"list": self.parseList(self.get(self.host + "/country/korea/"))}

    def categoryContent(self, tid, pg, filter, extend):
        path = str(tid or "country/korea").strip("/")
        if str(pg) == "1":
            url = f"{self.host}/{path}/"
        else:
            url = f"{self.host}/{path}/{pg}/"
        
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
        
        name = self.clean(self.match(html, r'<h1[^>]*>(.*?)</h1>') or self.match(html, r'<meta property="og:title" content="(.*?)"'))
        pic = self.fix(self.match(html, r'<meta property="og:image" content="(.*?)"') or self.match(html, r'<img[^>]+class=["\']package-image["\'][^>]*src=["\']([^"\']+)["\']'))
        desc = self.clean(self.match(html, r'<meta property="og:description" content="(.*?)"'))
        remarks = self.clean(self.match(html, r'<div[^>]+class=["\']ep["\'][^>]*>(.*?)</div>'))
        
        episodes_nano = []
        episodes_ok = []
        seen_ep = set()
        
        pattern = r'<[^>]+(?:href|data-link|data-src|data-url)=["\']([^"\']+)["\'][^>]*>(.*?)</[^>]+>'
        ep_links = []
        for link, text in re.findall(pattern, html, re.I | re.S):
            clean_text = self.clean(text)
            ep_match = re.search(r'(?:EP\.?|ตอนที่|\b)?\s*(\d+)', clean_text, re.I)
            if ep_match:
                ep_num = str(int(ep_match.group(1)))
                if ep_num not in seen_ep and len(ep_num) <= 3 and not any(x in link for x in ["/category/", "/country/", "/page/", "/tag/"]):
                    seen_ep.add(ep_num)
                    ep_links.append((int(ep_num), self.fix(link)))

        ep_links.sort(key=lambda x: x[0])

        for ep_num, ep_url in ep_links:
            # ใช้ URL ของแต่ละตอนโดยตรง
            episodes_nano.append(f"ตอนที่ {ep_num}${ep_url}#nano")
            episodes_ok.append(f"ตอนที่ {ep_num}${ep_url}#ok")

        if not episodes_nano:
            episodes_nano.append(f"เล่นMain${vid}#nano")
            episodes_ok.append(f"เล่นMain${vid}#ok")

        play_from = "Nanoplayer$$$OK.ru"
        play_url = "#".join(episodes_nano) + "$$$" + "#".join(episodes_ok)

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
                "vod_play_from": play_from,
                "vod_play_url": play_url
            }]
        }

    def searchContent(self, key, quick, pg="1"):
        q = quote(key)
        url = f"{self.host}/search?name={q}"
        html = self.get(url)
        return {"list": self.parseList(html), "page": int(pg)}

    def parse_ok_ru(self, embed_url):
        """ดึง Direct Video Link จาก OK.ru API"""
        try:
            video_id = re.search(r'videoembed/(\d+)', embed_url) or re.search(r'video/(\d+)', embed_url)
            if not video_id:
                return ""
            vid = video_id.group(1)
            
            api_url = f"https://ok.ru/dk?cmd=videoPlayerMetadata&vId={vid}"
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "X-Requested-With": "XMLHttpRequest"
            }
            res = requests.post(api_url, headers=headers, timeout=8, verify=False)
            data = res.json()
            
            if "hlsManifestUrl" in data:
                return data["hlsManifestUrl"]
            
            if "videos" in data:
                videos = data["videos"]
                for quality in ["full", "hd", "sd", "low"]:
                    for v in videos:
                        if v.get("name") == quality and "url" in v:
                            return v["url"]
                if len(videos) > 0 and "url" in videos[-1]:
                    return videos[-1]["url"]
        except Exception:
            pass
        return ""

    def playerContent(self, flag, id, vipFlags):
        # แยก hash tag ที่เติมไว้ใน detailContent
        target_mode = "nano"
        url = id
        if "#" in id:
            parts = id.split("#")
            url = parts[0]
            target_mode = parts[-1]

        try:
            html = self.get(url)
            iframes = re.findall(r'<iframe[^>]+(?:src|data-src)=["\']([^"\']+)["\']', html, re.I)
            
            nano_iframe = ""
            ok_iframe = ""

            # กรองและคัดเลือกเฉพาะ iframe ตัวเล่นจริง (ข้ามโฆษณา)
            for iframe_url in iframes:
                iframe_url = self.fix(iframe_url)
                
                # ข้ามโฆษณา UFAZEED / Banner ต่างๆ
                if any(x in iframe_url.lower() for x in ["ufazeed", "banner", "ads", "popup"]):
                    continue
                    
                if "nanoplayer" in iframe_url or "player.php" in iframe_url or "sv3.php" in iframe_url:
                    nano_iframe = iframe_url
                elif "ok.ru" in iframe_url:
                    ok_iframe = iframe_url

            # 1. กรณีเลือกแท็บ OK.ru
            if target_mode == "ok" or flag == "OK.ru":
                if ok_iframe:
                    direct_url = self.parse_ok_ru(ok_iframe)
                    if direct_url:
                        return {
                            "parse": 0,
                            "playUrl": "",
                            "url": direct_url,
                            "header": {
                                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                                "Referer": "https://ok.ru/"
                            }
                        }
                    return {
                        "parse": 1,
                        "playUrl": "",
                        "url": ok_iframe,
                        "header": {"User-Agent": self.headers["User-Agent"], "Referer": "https://ok.ru/"}
                    }

            # 2. กรณีเลือกแท็บ Nanoplayer
            if nano_iframe:
                return {
                    "parse": 1,
                    "playUrl": "",
                    "url": nano_iframe,
                    "header": {
                        "User-Agent": self.headers["User-Agent"],
                        "Referer": url
                    },
                    "rule": ".*?(?:hls2\.php|m3u8|(?<!we356|me356|supreme)\.mp4).*"
                }

        except Exception:
            pass

        return {
            "parse": 1,
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
            r = requests.get(url, headers=h, timeout=10, verify=False)
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
            r = requests.get(url, headers=self.headers, timeout=10, verify=False)
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
        if not html:
            return res
        
        cards = re.findall(r'<a\s+class=["\']card["\']\s+href=["\']([^"\']+)["\']>(.*?)</a>', html, re.S | re.I)
        if cards:
            for href, block in cards:
                url = self.fix(href)
                img_m = re.search(r'<img[^>]+src=["\']([^"\']+)["\']', block, re.I)
                pic = img_m.group(1) if img_m else ""
                
                title_m = re.search(r'<div\s+class=["\']title["\']\s*>(.*?)</div>', block, re.S | re.I)
                if not title_m:
                    title_m = re.search(r'alt=["\']([^"\']+)["\']', block, re.I)
                title = self.clean(title_m.group(1)) if title_m else ""
                
                ep_m = re.search(r'<div\s+class=["\']ep["\']\s*>(.*?)</div>', block, re.S | re.I)
                remarks = self.clean(ep_m.group(1)) if ep_m else ""
                
                if url and title:
                    res.append({
                        "vod_id": url,
                        "vod_name": title,
                        "vod_pic": self.img(pic),
                        "vod_remarks": remarks
                    })
            return res

        seen = set()
        for m in re.finditer(r'<a\s+[^>]*href=["\']([^"\']+)["\'][^>]*>(.*?)</a>', html, re.S | re.I):
            href, block = m.group(1), m.group(2)
            url = self.fix(href)
            
            if url in seen or any(x in url for x in ["/category/", "/country/", "/page/", "/tag/", "#", "mailto:", "tel:"]):
                continue
                
            img_m = re.search(r'<img[^>]+(?:src|data-src)=["\']([^"\']+)["\']', block, re.I)
            if not img_m:
                continue
            pic = img_m.group(1)
            
            title_m = re.search(r'alt=["\']([^"\']+)["\']', block, re.I)
            title = self.clean(title_m.group(1)) if title_m else self.clean(block)
            
            if title and len(title) > 1 and "logo" not in pic:
                seen.add(url)
                res.append({
                    "vod_id": url,
                    "vod_name": title,
                    "vod_pic": self.img(pic),
                    "vod_remarks": ""
                })
                
        return res
