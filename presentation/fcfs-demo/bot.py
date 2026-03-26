import asyncio
import aiohttp
import sys
import time
import random
from datetime import datetime

URL = sys.argv[3] if len(sys.argv) > 3 else "http://localhost:8081"
COUPON_ID = int(sys.argv[1]) if len(sys.argv) > 1 else 1
BOT_COUNT = int(sys.argv[2]) if len(sys.argv) > 2 else 999


async def get_open_at(session):
    async with session.get(f"{URL}/api/fcfs/{COUPON_ID}/status") as resp:
        data = await resp.json()
        return data.get("openAt", "")


async def fire(session, member_id):
    try:
        # 정규분포 지연: 평균 0.5초, 표준편차 0.3초
        # 대부분 0.2~0.8초에 집중, 소수만 0초/1.5초+
        delay = max(0, random.gauss(0.5, 0.3))
        await asyncio.sleep(delay)
        async with session.post(
            f"{URL}/api/fcfs/{COUPON_ID}/issue",
            headers={"X-Member-Id": str(member_id)},
        ) as resp:
            data = await resp.json()
            return data.get("success", False)
    except Exception:
        return False


async def main():
    connector = aiohttp.TCPConnector(limit=0)
    async with aiohttp.ClientSession(connector=connector) as session:
        # 오픈 시각 조회 + 대기
        open_at_str = await get_open_at(session)
        if open_at_str:
            open_at = datetime.fromisoformat(open_at_str)
            print(f"오픈 시각: {open_at}")
            print(f"봇 {BOT_COUNT}마리 대기 중...")
            while datetime.now() < open_at:
                remaining = (open_at - datetime.now()).total_seconds()
                print(f"\r⏳ {remaining:.1f}초 남음...", end="", flush=True)
                await asyncio.sleep(0.05)
            print("\n🚀 발사!")
        else:
            print(f"오픈 시각 미설정 — 즉시 발사! (봇 {BOT_COUNT}마리)")

        # 봇 동시 발사 (정규분포 지연 적용)
        start = time.time()
        tasks = [fire(session, 10000 + i) for i in range(BOT_COUNT)]
        results = await asyncio.gather(*tasks)
        elapsed = time.time() - start

        success = sum(1 for r in results if r)
        fail = BOT_COUNT - success
        print(f"\n📊 결과: 성공 {success} / 실패 {fail} ({elapsed:.2f}초)")


if __name__ == "__main__":
    asyncio.run(main())
