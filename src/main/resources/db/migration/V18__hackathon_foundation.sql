-- V18: 해커톤 공통 기반 — 데모 계정·보증금 대상 플래그. (KAN-399)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS deposit_required BOOLEAN NOT NULL DEFAULT false;
