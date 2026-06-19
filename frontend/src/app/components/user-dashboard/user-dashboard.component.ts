import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { FinanceService, WalletResponse } from '../../services/finance.service';

@Component({
  selector: 'app-user-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './user-dashboard.component.html',
  styleUrls: ['./user-dashboard.component.css']
})
export class UserDashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private financeService = inject(FinanceService);
  private router = inject(Router);

  // User details
  userId = '';
  username = '';
  userRole = '';
  userStatus = 'PENDING';

  // Wallet
  wallet: WalletResponse | null = null;

  // Forms data
  transferData = { recipient: '', amount: 0, note: '' };
  voucherCode = '';
  qrisInvoiceId = '';

  // UI state variables
  transferError: string | null = null;
  transferSuccess: string | null = null;
  transferLoading = false;

  voucherError: string | null = null;
  voucherSuccess: string | null = null;
  voucherLoading = false;

  qrisError: string | null = null;
  qrisSuccess: string | null = null;
  qrisLoading = false;
  qrisTargetTx: any = null; // Stored target transaction before payment

  ngOnInit(): void {
    this.userId = this.authService.getUserId() || '';
    this.username = this.authService.getUserId() ? 'User' : 'Guest'; // We will display general user greetings
    this.userRole = this.authService.getUserRole() || '';
    this.userStatus = this.authService.getUserStatus() || 'PENDING';
    
    // Let's call getMyWallet to test connection and load wallet details
    this.loadWallet();
  }

  loadWallet(): void {
    this.financeService.getMyWallet().subscribe({
      next: (res) => {
        this.wallet = res;
      },
      error: (err) => {
        console.error('Failed to load wallet', err);
      }
    });
  }

  onTransfer(): void {
    if (!this.transferData.recipient || this.transferData.amount <= 0) {
      this.transferError = 'Recipient dan nominal transfer harus diisi dengan benar.';
      return;
    }

    this.transferLoading = true;
    this.transferError = null;
    this.transferSuccess = null;

    this.financeService.transfer(
      this.transferData.recipient, 
      this.transferData.amount, 
      this.transferData.note
    ).subscribe({
      next: (res) => {
        this.transferSuccess = `Transfer sebesar Rp${res.amount.toLocaleString()} ke ${this.transferData.recipient} BERHASIL. Status: ${res.status}`;
        this.transferData = { recipient: '', amount: 0, note: '' };
        this.transferLoading = false;
        this.loadWallet(); // Reload balance
      },
      error: (err) => {
        console.error(err);
        this.transferError = err.error?.message || 'Transfer gagal. Periksa kembali username/email penerima dan saldo Anda.';
        this.transferLoading = false;
      }
    });
  }

  onRedeemVoucher(): void {
    if (!this.voucherCode) {
      this.voucherError = 'Kode voucher tidak boleh kosong.';
      return;
    }

    this.voucherLoading = true;
    this.voucherError = null;
    this.voucherSuccess = null;

    this.financeService.redeemVoucher(this.voucherCode).subscribe({
      next: (res) => {
        this.voucherSuccess = `Klaim voucher berhasil! Saldo bertambah Rp${res.amount.toLocaleString()}.`;
        this.voucherCode = '';
        this.voucherLoading = false;
        this.loadWallet();
      },
      error: (err) => {
        console.error(err);
        this.voucherError = err.error?.message || 'Voucher tidak valid atau sudah pernah diklaim.';
        this.voucherLoading = false;
      }
    });
  }

  // Fetch invoice details before paying
  onCheckQris(): void {
    if (!this.qrisInvoiceId) {
      this.qrisError = 'ID Invoice QRIS tidak boleh kosong.';
      return;
    }

    this.qrisLoading = true;
    this.qrisError = null;
    this.qrisSuccess = null;
    this.qrisTargetTx = null;

    this.financeService.getTransactionStatus(this.qrisInvoiceId).subscribe({
      next: (res) => {
        this.qrisTargetTx = res;
        this.qrisLoading = false;
        if (res.status !== 'PENDING') {
          this.qrisError = `Invoice ini sudah diselesaikan dengan status: ${res.status}`;
          this.qrisTargetTx = null;
        }
      },
      error: (err) => {
        console.error(err);
        this.qrisError = 'Invoice QRIS tidak ditemukan.';
        this.qrisLoading = false;
      }
    });
  }

  onPayQris(): void {
    if (!this.qrisTargetTx) return;

    this.qrisLoading = true;
    this.qrisError = null;

    this.financeService.payQris(this.qrisTargetTx.invoiceId).subscribe({
      next: (res) => {
        this.qrisSuccess = `Pembayaran QRIS sebesar Rp${res.amount.toLocaleString()} untuk ${res.recipient?.merchantName || 'Merchant'} BERHASIL.`;
        this.qrisTargetTx = null;
        this.qrisInvoiceId = '';
        this.qrisLoading = false;
        this.loadWallet();
      },
      error: (err) => {
        console.error(err);
        this.qrisError = err.error?.message || 'Pembayaran QRIS gagal. Saldo tidak mencukupi atau terjadi kesalahan.';
        this.qrisLoading = false;
      }
    });
  }

  cancelQrisPay(): void {
    this.qrisTargetTx = null;
    this.qrisInvoiceId = '';
  }

  onLogout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login']);
      },
      error: () => {
        this.authService.clearSession();
        this.router.navigate(['/login']);
      }
    });
  }
}
