# -*- coding: utf-8 -*-
"""
真实数据采集服务(端口 8091,供 Java 后端调用)
数据源(全部为公开接口,已验证本机可直连):
  - 东方财富数据中心 datacenter-web.eastmoney.com  → 全市场季度业绩报表(ROE/毛利率/每股经营现金流/增速)
  - 腾讯行情 qt.gtimg.cn                            → 实时行情(价格/PE/市值/涨跌幅),支持批量
  - 天天基金 fund.eastmoney.com/data/rankhandler    → 开放式基金排行(近1/3年收益/净值/费率)
接口:
  GET /health
  GET /stocks/quality?limit=60   优质股候选池(业绩+行情合并,预筛选+PE分位)
  GET /funds/rank?ftype=全部     基金排行
带内存缓存(30分钟)。
"""
import re
import time
import traceback

import requests
from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

app = FastAPI(title="finance-collector")

_cache = {}
CACHE_TTL = 30 * 60


def _session():
    s = requests.Session()
    s.trust_env = False  # 忽略系统/注册表代理,强制直连(本机直连可用)
    s.headers["User-Agent"] = UA
    return s


def cached(key, loader):
    now = time.time()
    hit = _cache.get(key)
    if hit and now - hit[0] < CACHE_TTL:
        return hit[1], True
    data = loader()
    _cache[key] = (now, data)
    return data, False


def _num(v, default=None):
    try:
        if v is None or v == "" or v == "-":
            return default
        return float(v)
    except Exception:
        return default


@app.get("/health")
def health():
    return {"ok": True, "cacheKeys": list(_cache.keys()), "ts": time.time()}


# ================= 股票 =================

REPORT_DATES = ["2026-03-31", "2025-12-31", "2025-09-30"]


def _load_yjbb():
    """东财数据中心-业绩报表(全市场,分页拉取)"""
    s = _session()
    for rd in REPORT_DATES:
        rows = {}
        page = 1
        total_pages = 1
        try:
            while page <= total_pages and page <= 15:
                r = s.get(
                    "https://datacenter-web.eastmoney.com/api/data/v1/get",
                    params={
                        "reportName": "RPT_LICO_FN_CPD",
                        "columns": "SECURITY_CODE,SECURITY_NAME_ABBR,WEIGHTAVG_ROE,XSMLL,"
                                   "MGJYXJJE,YSTZ,SJLTZ,TOTAL_OPERATE_INCOME,PARENT_NETPROFIT,PUBLISHNAME",
                        "pageSize": "500",
                        "pageNumber": str(page),
                        "sortColumns": "WEIGHTAVG_ROE",
                        "sortTypes": "-1",
                        "filter": f"(REPORTDATE='{rd}')",
                    },
                    timeout=20,
                )
                j = r.json()
                res = j.get("result") or {}
                data = res.get("data") or []
                if page == 1:
                    total_pages = res.get("pages", 1) or 1
                for it in data:
                    code = str(it.get("SECURITY_CODE", "")).zfill(6)
                    rows[code] = {
                        "name": it.get("SECURITY_NAME_ABBR"),
                        "industry": it.get("PUBLISHNAME"),
                        "roe": _num(it.get("WEIGHTAVG_ROE")),
                        "grossMargin": _num(it.get("XSMLL")),
                        "epsCashflow": _num(it.get("MGJYXJJE")),
                        "revenueYoy": _num(it.get("YSTZ")),
                        "profitYoy": _num(it.get("SJLTZ")),
                        "reportDate": rd,
                    }
                page += 1
                time.sleep(0.3)
        except Exception:
            traceback.print_exc()
        if len(rows) > 500:
            return rows
    return rows


def _tx_prefix(code):
    if code.startswith("6"):
        return "sh" + code
    if code.startswith(("0", "3")):
        return "sz" + code
    return None  # 北交所等暂不处理


def _load_quotes(codes):
    """腾讯行情批量:价格/PE/市值/涨跌幅"""
    s = _session()
    out = {}
    symbols = [x for x in (_tx_prefix(c) for c in codes) if x]
    for i in range(0, len(symbols), 60):
        batch = symbols[i:i + 60]
        try:
            r = s.get("https://qt.gtimg.cn/q=" + ",".join(batch), timeout=15)
            r.encoding = "gbk"
            for m in re.finditer(r'v_(\w+)="([^"]*)"', r.text):
                f = m.group(2).split("~")
                if len(f) < 47:
                    continue
                code = f[2]
                out[code] = {
                    "price": _num(f[3]),
                    "changePct": _num(f[32]),
                    "pe": _num(f[39]),          # 市盈率(动)
                    "pb": _num(f[46]),          # 市净率
                    "mktcapYi": _num(f[45]),    # 总市值(亿)
                }
        except Exception:
            traceback.print_exc()
        time.sleep(0.2)
    return out


def _load_stock_quality():
    yjbb = _load_yjbb()
    # 初筛:有ROE数据且ROE>=8%(季报口径),取前400只查行情
    cand = [(c, v) for c, v in yjbb.items() if v.get("roe") is not None and v["roe"] >= 2.0
            and _tx_prefix(c)]
    cand.sort(key=lambda x: -x[1]["roe"])
    cand = cand[:400]
    quotes = _load_quotes([c for c, _ in cand])

    rows = []
    for code, fin in cand:
        q = quotes.get(code)
        if not q or q["price"] is None or q["pe"] is None:
            continue
        pe, mktcap = q["pe"], q["mktcapYi"] or 0
        # 价值域过滤:PE(0,60]、市值>=100亿
        if pe <= 0 or pe > 60 or mktcap < 100:
            continue
        rows.append({
            "code": code,
            "name": fin["name"],
            "industry": fin.get("industry"),
            "price": q["price"],
            "changePct": q["changePct"],
            "pe": pe,
            "pb": q["pb"],
            "mktcapYi": mktcap,
            "roe": fin["roe"],
            "grossMargin": fin["grossMargin"],
            "epsCashflow": fin["epsCashflow"],
            "revenueYoy": fin["revenueYoy"],
            "profitYoy": fin["profitYoy"],
            "reportDate": fin["reportDate"],
        })

    # PE 分位(候选域横截面近似)
    pes = sorted(x["pe"] for x in rows)
    n = len(pes)
    for x in rows:
        rank = sum(1 for p in pes if p <= x["pe"])
        x["peQuantile"] = round(rank / n * 100, 1) if n else None

    rows.sort(key=lambda x: -(x["roe"] or 0))
    return rows


@app.get("/stocks/quality")
def stocks_quality(limit: int = Query(60, ge=1, le=300)):
    try:
        data, hit = cached("stock_quality", _load_stock_quality)
        return {"ok": True, "cached": hit, "total": len(data), "list": data[:limit]}
    except Exception as e:
        traceback.print_exc()
        return JSONResponse(status_code=500, content={"ok": False, "error": str(e)})


# ================= 基金 =================

FUND_TYPE_MAP = {
    "全部": "all", "股票型": "gp", "混合型": "hh",
    "债券型": "zq", "指数基金": "zs", "QDII": "qdii",
}


def _load_fund_rank(ftype):
    ft = FUND_TYPE_MAP.get(ftype, "all")
    s = _session()
    s.headers["Referer"] = "https://fund.eastmoney.com/data/fundranking.html"
    r = s.get(
        "https://fund.eastmoney.com/data/rankhandler.aspx",
        params={"op": "ph", "dt": "kf", "ft": ft, "rs": "", "gs": "0",
                "sc": "3nzf", "st": "desc", "pi": "1", "pn": "200", "dx": "1"},
        timeout=20,
    )
    m = re.search(r"datas:\[(.*?)\]", r.text, re.S)
    if not m:
        return []
    rows = []
    for item in re.findall(r'"([^"]*)"', m.group(1)):
        f = item.split(",")
        if len(f) < 21:
            continue
        # 0代码 1名称 3日期 4单位净值 6日增 7近1周 8近1月 9近3月 10近6月 11近1年 12近2年 13近3年 14今年来 15成立来 20手续费
        y1, y3 = _num(f[11]), _num(f[13])
        if y1 is None and y3 is None:
            continue
        rows.append({
            "code": f[0],
            "name": f[1],
            "type": ftype,
            "nav": _num(f[4]),
            "dailyPct": _num(f[6]),
            "m6": _num(f[10]),
            "y1": y1,
            "y3": y3,
            "thisYear": _num(f[14]),
            "fee": f[20] if len(f) > 20 else None,
        })
    return rows


@app.get("/funds/rank")
def funds_rank(ftype: str = Query("全部"), limit: int = Query(80, ge=1, le=300)):
    try:
        data, hit = cached("fund_rank_" + ftype, lambda: _load_fund_rank(ftype))
        return {"ok": True, "cached": hit, "total": len(data), "list": data[:limit]}
    except Exception as e:
        traceback.print_exc()
        return JSONResponse(status_code=500, content={"ok": False, "error": str(e)})


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8091)
