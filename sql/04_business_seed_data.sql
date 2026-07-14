-- Run this script once after 03_business_schema.sql on an empty ai_business database.
-- It uses a temporary number table instead of CTE INSERT syntax so it runs reliably in Navicat + MySQL 8.
USE ai_business;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_sequence;
CREATE TEMPORARY TABLE tmp_seed_sequence AS
SELECT d0.n + d1.n * 10 + d2.n * 100 + d3.n * 1000 + 1 AS n
FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3;

INSERT INTO biz_customer (customer_name, customer_level, region, industry, created_at)
SELECT CONCAT('企业客户', LPAD(n, 3, '0')),
       ELT(1 + MOD(n, 4), 'VIP', 'A', 'B', 'C'),
       ELT(1 + MOD(n, 6), '华东', '华南', '华北', '华中', '西南', '西北'),
       ELT(1 + MOD(n, 5), '制造业', '零售', '互联网', '教育', '医疗'),
       DATE_SUB(NOW(), INTERVAL MOD(n * 13, 900) DAY)
FROM tmp_seed_sequence
WHERE n <= 100;

INSERT INTO biz_product (product_name, category, price, stock)
SELECT CONCAT('商品', LPAD(n, 3, '0')),
       ELT(1 + MOD(n, 5), '办公设备', '软件服务', '电子配件', '企业培训', '营销服务'),
       99.00 + MOD(n * 37, 900) + 0.90,
       100 + MOD(n * 23, 900)
FROM tmp_seed_sequence
WHERE n <= 50;

INSERT INTO biz_order (order_no, customer_id, amount, status, order_date, created_at)
SELECT CONCAT('ORD', DATE_FORMAT(CURDATE(), '%Y%m%d'), LPAD(n, 6, '0')),
       1 + MOD(n - 1, 100),
       0.00,
       ELT(1 + MOD(n, 4), 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'),
       DATE_SUB(CURDATE(), INTERVAL MOD(n * 7, 540) DAY),
       NOW()
FROM tmp_seed_sequence
WHERE n <= 1000;

INSERT INTO biz_order_item (order_id, product_id, quantity, unit_price, subtotal)
SELECT CEIL(n / 2),
       1 + MOD(n * 7, 50),
       1 + MOD(n, 5),
       99.90 + MOD(n * 37, 900),
       (1 + MOD(n, 5)) * (99.90 + MOD(n * 37, 900))
FROM tmp_seed_sequence
WHERE n <= 2000;

UPDATE biz_order o
JOIN (
    SELECT order_id, SUM(subtotal) AS total_amount
    FROM biz_order_item
    GROUP BY order_id
) i ON i.order_id = o.id
SET o.amount = i.total_amount;

DROP TEMPORARY TABLE tmp_seed_sequence;
