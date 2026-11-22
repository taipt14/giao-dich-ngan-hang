---
id: BANK-007
epic: E0
title: CI: GitHub Actions build và test
priority: Low
depends_on: [BANK-006]
status: TODO
---

# BANK-007: CI: GitHub Actions build và test

## Bối cảnh nghiệp vụ
Mỗi lần push cần tự động kiểm tra build và test.

## Phạm vi kỹ thuật
- File `.github/workflows/ci.yml`

## Yêu cầu chi tiết
1. Trigger khi push và pull request lên `main`
2. Setup JDK 21, cache Maven
3. Chạy `mvn clean verify` (Testcontainers chạy được trên runner ubuntu)

## Acceptance criteria
- [ ] Workflow chạy xanh trên một commit mẫu
- [ ] Test fail → workflow fail

## Ngoài phạm vi
- Build Docker image, deploy

## Phụ thuộc
- [BANK-006: common-test: base class Testcontainers dùng chung](BANK-006-common-test-testcontainers.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
