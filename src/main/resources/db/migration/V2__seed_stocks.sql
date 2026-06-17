-- 4단계 검증용 테스트 종목 시드. 5단계에서 외부 API 기반 lazy 등록으로 대체 예정.
INSERT INTO stock (ticker, name, market) VALUES
    ('005930', '삼성전자',       'KOSPI'),
    ('000660', 'SK하이닉스',     'KOSPI'),
    ('035420', 'NAVER',          'KOSPI'),
    ('035720', '카카오',          'KOSPI'),
    ('051910', 'LG화학',          'KOSPI'),
    ('005380', '현대차',          'KOSPI'),
    ('068270', '셀트리온',        'KOSPI'),
    ('247540', '에코프로비엠',     'KOSDAQ'),
    ('086520', '에코프로',         'KOSDAQ'),
    ('091990', '셀트리온헬스케어', 'KOSDAQ');
