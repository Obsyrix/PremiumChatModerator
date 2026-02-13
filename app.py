import aiohttp
import asyncio

API_URL = "https://profanity-api.xeven.workers.dev"

async def check_profanity(message):
    async with aiohttp.ClientSession() as session:
        async with session.post(API_URL, json={"message": message}) as resp:
            if resp.status != 200:
                raise RuntimeError(f"HTTP {resp.status}")
            return await resp.json()

async def main():
    while True:
        print(await check_profanity("Hey fuck you asshole, i hope you die"))

asyncio.run(main())
