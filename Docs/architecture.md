# Arsitektur Proyek E.O.P - Pemetaan API Endpoints

Dokumen ini memetakan rute panggilan API dari Klien (Angular) melalui API Gateway menuju endpoint REST masing-masing microservice dalam bentuk bagan pohon teks (ASCII Tree).

---

## Bagan Pemetaan Rute API

```text
Client (Angular)
   │
   └──► E.O.P Gateway (Port 8080)
         │
         ├──► [Identity Service - Port 8081]
         │     ├── POST  /api/auth/login
         │     ├── POST  /api/auth/register
         │     ├── POST  /api/auth/register/merchant
         │     ├── POST  /api/auth/refresh
         │     ├── POST  /api/auth/logout
         │     ├── PATCH /api/admin/users/{userId}/kyc
         │     ├── PATCH /api/admin/users/{userId}/suspend
         │     └── PATCH /api/admin/merchants/{merchantId}/verify
         │
         ├──► [Core Finance Service - Port 8082]
         │     ├── GET   /api/finance/wallet
         │     ├── POST  /api/finance/transfer
         │     ├── POST  /api/finance/qris/generate
         │     ├── POST  /api/finance/qris/pay
         │     ├── POST  /api/finance/voucher/redeem
         │     ├── GET   /api/finance/transactions/{invoiceId}
         │     ├── GET   /api/finance/export/transactions
         │     └── GET   /api/finance/export/transactions/template
         │
         └──► [Support & Oracle Service - Port 8083]
               ├── POST  /api/support/complaints
               ├── GET   /api/support/complaints/my
               ├── GET   /api/support/complaints/{complaintId}
               ├── GET   /api/support/complaints/status/{status}
               ├── PATCH /api/support/complaints/{complaintId}/status
               ├── GET   /api/support/export/complaints
               └── GET   /api/support/export/complaints/template
```
