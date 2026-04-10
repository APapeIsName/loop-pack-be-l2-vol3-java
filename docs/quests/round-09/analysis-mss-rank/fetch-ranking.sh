#!/bin/bash
BASE_DIR="/Users/gongmyeongseon/spring-study/loop-pack-be-l2-vol3-java/docs/quests/round-09/analysis-mss-rank"
MINUTE=$(date +%M)
MOD=$((10#$MINUTE % 3))

if [ $MOD -eq 0 ]; then SID=199; DIR=all;
elif [ $MOD -eq 1 ]; then SID=200; DIR=new-up;
else SID=201; DIR=up-quickly; fi

TIMESTAMP=$(date +%H%M)
mkdir -p "${BASE_DIR}/${DIR}"
curl -s "https://api.musinsa.com/api2/hm/web/v5/pans/ranking?storeCode=musinsa&sectionId=${SID}&gf=A&contentsId=&categoryCode=000&ageBand=AGE_BAND_ALL&subPan=product" -o "${BASE_DIR}/${DIR}/${TIMESTAMP}.md"
