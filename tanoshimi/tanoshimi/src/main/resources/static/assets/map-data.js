const KOREA_REGIONS = {
  capital: { name:'수도권', cx:40, cy:25 },
  gangwon: { name:'강원', cx:65, cy:20 },
  chungbuk: { name:'충북', cx:55, cy:40 },
  chungnam: { name:'충남', cx:32, cy:52 },
  gyeongbuk: { name:'경북', cx:72, cy:55 },
  gyeongnam: { name:'경남', cx:62, cy:82 },
  jeonbuk: { name:'전북', cx:38, cy:70 },
  jeonnam: { name:'전남', cx:30, cy:95 },
  jeju: { name:'제주', cx:25, cy:130 }
};

const JAPAN_REGIONS = {
  hokkaido: { name:'홋카이도', cx:64, cy:18 },
  aomori: { name:'아오모리', cx:72, cy:35 },
  iwate: { name:'이와테', cx:78, cy:44 },
  miyagi: { name:'미야기', cx:78, cy:51 },
  akita: { name:'아키타', cx:70, cy:44 },
  yamagata: { name:'야마가타', cx:72, cy:50 },
  fukushima: { name:'후쿠시마', cx:75, cy:56 },
  tokyo: { name:'도쿄', cx:72, cy:68 },
  kanagawa: { name:'가나가와', cx:70, cy:70 },
  saitama: { name:'사이타마', cx:70, cy:65 },
  chiba: { name:'지바', cx:75, cy:68 },
  ibaraki: { name:'이바라키', cx:77, cy:63 },
  tochigi: { name:'도치기', cx:74, cy:61 },
  gunma: { name:'군마', cx:68, cy:61 },
  yamanashi: { name:'야마나시', cx:65, cy:67 },
  shizuoka: { name:'시즈오카', cx:65, cy:72 },
  aichi: { name:'아이치', cx:57, cy:71 },
  mie: { name:'미에', cx:53, cy:73 },
  shiga: { name:'시가', cx:50, cy:68 },
  kyoto: { name:'교토', cx:49, cy:66 },
  osaka: { name:'오사카', cx:47, cy:70 },
  hyogo: { name:'효고', cx:43, cy:67 },
  nara: { name:'나라', cx:50, cy:71 },
  wakayama: { name:'와카야마', cx:49, cy:75 },
  tottori: { name:'돗토리', cx:40, cy:64 },
  shimane: { name:'시마네', cx:33, cy:65 },
  okayama: { name:'오카야마', cx:40, cy:68 },
  hiroshima: { name:'히로시마', cx:33, cy:70 },
  yamaguchi: { name:'야마구치', cx:24, cy:72 },
  tokushima: { name:'도쿠시마', cx:42, cy:76 },
  kagawa: { name:'카가와', cx:40, cy:73 },
  ehime: { name:'에히메', cx:34, cy:76 },
  kochi: { name:'고치', cx:36, cy:79 },
  fukuoka: { name:'후쿠오카', cx:23, cy:76 },
  saga: { name:'사가', cx:19, cy:77 },
  oita: { name:'오이타', cx:26, cy:78 },
  kumamoto: { name:'구마모토', cx:22, cy:81 },
  miyazaki: { name:'미야자키', cx:26, cy:85 },
  kagoshima: { name:'가고시마', cx:21, cy:89 },
  okinawa: { name:'오키나와', cx:15, cy:15 },
  nagasaki: { name:'나가사키', cx:16, cy:79 }
};

const FAMOUS_PLACES = {
  hokkaido: [{ name:'오도리 공원', desc:'삿포로의 상징적인 도심 공원', ph:'ph1' }, { name:'후라노 라벤더', desc:'여름을 수놓는 보랏빛 융단', ph:'ph2' }, { name:'오타루 운하', desc:'시간윴 멈춘 듯한 로맨틱 운하', ph:'ph3' }, { name:'노보리베츠 지옥계곡', desc:'끓어오르는 온천수의 신비', ph:'ph4' }],
  tokyo: [{ name:'도쿄 타워', desc:'도쿄의 빛나는 랜드마크', ph:'ph1' }, { name:'센소지', desc:'아사쿠사의 오래된 정취', ph:'ph2' }, { name:'시부야 스크램블', desc:'세계 최대의 교차로', ph:'ph3' }, { name:'도쿄 스카이트리', desc:'하늘 위에서 보는 도쿄 전경', ph:'ph4' }],
  osaka: [{ name:'도톤보리', desc:'네온사인과 먹거리의 거리', ph:'ph1' }, { name:'오사카성', desc:'오사카의 상징, 벚꽃 명소', ph:'ph2' }, { name:'유니버설 스튜디오', desc:'테마파크, 엔터테인먼트', ph:'ph3' }, { name:'우메다 공중정원', desc:'오사카 시내 전망대', ph:'ph4' }, { name:'쿠로몬 시장', desc:'신선한 해산물 시장', ph:'ph1' }],
  kyoto: [{ name:'기요미즈데라', desc:'교토를 대표하는 목조 사찰', ph:'ph1' }, { name:'후시미이나리', desc:'천개의 붉은 도리이', ph:'ph2' }, { name:'아라시야마 대나무숲', desc:'초록빛 대나무 산책로', ph:'ph3' }, { name:'기온 거리', desc:'전통 마치야와 게이샤 문화', ph:'ph4' }],
  fukuoka: [{ name:'나카스 라멘 스트리트', desc:'돈코츠 라멘의 본고장', ph:'ph1' }, { name:'다자이후 텐만구', desc:'학문의 신을 모신 신사', ph:'ph2' }, { name:'야나가와 뱃놀이', desc:'수로를 따라가는 뱃놀이', ph:'ph3' }, { name:'모모치 해변', desc:'후쿠오카 타워와 인공 해변', ph:'ph4' }],
  okinawa: [{ name:'추라우미 수족관', desc:'고라상어와 만타가오리 코타', ph:'ph1' }, { name:'아메리칸 빌리지', desc:'미국 서부 느낌의 핫플레이스', ph:'ph2' }, { name:'만자모', desc:'코끼리를 닮은 절벽 풍경', ph:'ph3' }, { name:'국제거리', desc:'나하 시내 대규모 번화가', ph:'ph4' }],
  capital: [{ name:'경복궁', desc:'조선 왕조의 법궁', ph:'ph1' }, { name:'북촌 한옥마을', desc:'전통 한옥이 모인 골목', ph:'ph2' }, { name:'엔서울타워', desc:'서울 야경이 보이는 전망대', ph:'ph3' }, { name:'인천 차이나타운', desc:'이색적인 거리 풍경', ph:'ph4' }],
  gangwon: [{ name:'설악산', desc:'단풍과 기산으로 유명', ph:'ph1' }, { name:'강릉 안목해변', desc:'커피거리와 바다경관', ph:'ph2' }, { name:'남이섬', desc:'가로수길이 예쁜 관광섬', ph:'ph3' }],
  gyeongbuk: [{ name:'경주 불국사', desc:'천년고도의 신라 사찰', ph:'ph1' }, { name:'안동 하회마을', desc:'조선시대 촌락 모습 그대로', ph:'ph2' }, { name:'포항 호미곶', desc:'상생의 손과 일출', ph:'ph3' }],
  gyeongnam: [{ name:'해운대 해수욕장', desc:'부산의 대표 해변', ph:'ph1' }, { name:'감천문화마을', desc:'알록달록 산동네 마을', ph:'ph2' }, { name:'통영 케이블카', desc:'한려수도가 보이는 전망', ph:'ph3' }],
  jeju: [{ name:'성산일출봉', desc:'유네스코 세계자연유산', ph:'ph1' }, { name:'한라산', desc:'제주의 중심, 백록담', ph:'ph2' }, { name:'협재해수욕장', desc:'에메랄드빛 바다', ph:'ph3' }, { name:'우도', desc:'제주 안의 또 다른 섬', ph:'ph4' }]
};

const NO_DATA_NOTE = '해당 지역의 명소는 아지 준비중입니다.';


// 자동 채우기: 명소가 빈 지역은 기본 명소 생성
if (typeof KOREA_REGIONS !== 'undefined') {
    Object.keys(KOREA_REGIONS).forEach(id => {
        if (!FAMOUS_PLACES[id]) {
            let n = KOREA_REGIONS[id].name;
            FAMOUS_PLACES[id] = [
                { name: n + ' 핫플레이스', desc: n + '에서 가장 핫한 장소', ph: 'ph1' },
                { name: n + ' 힐링스팟', desc: n + '의 조용한 힐링 장소', ph: 'ph2' },
                { name: n + ' 로컬 맛집', desc: n + ' 현지인 추천 맛집', ph: 'ph3' }
            ];
        }
    });
}
if (typeof JAPAN_REGIONS !== 'undefined') {
    Object.keys(JAPAN_REGIONS).forEach(id => {
        if (!FAMOUS_PLACES[id]) {
            let n = JAPAN_REGIONS[id].name;
            FAMOUS_PLACES[id] = [
                { name: n + ' 중심가', desc: n + ' 최고의 번화가 명소', ph: 'ph1' },
                { name: n + ' 로컬 투어', desc: n + ' 현지 감성이 살아있는 곳', ph: 'ph2' },
                { name: n + ' 특산물 맛집', desc: n + ' 여행의 피로를 풀어주는 맛', ph: 'ph3' },
                { name: n + ' 야경 스팟', desc: n + ' 아름다운 밤풍경을 즐기는 곳', ph: 'ph4' }
            ];
        }
    });
}
