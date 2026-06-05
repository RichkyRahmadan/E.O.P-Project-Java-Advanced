import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { FinanceService, WalletResponse, TransactionResponse } from '../../services/finance.service';

@Component({
  selector: 'app-merchant-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './merchant-dashboard.component.html',
  styleUrls: ['./merchant-dashboard.component.css']
})
export class MerchantDashboardComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private financeService = inject(FinanceService);
  private router = inject(Router);

  // Merchant details
  userId = '';
  username = '';
  userRole = '';
  userStatus = 'PENDING';

  // Wallet
  wallet: WalletResponse | null = null;

  // Invoice Generation Form
  qrisForm = { amount: 0, note: '' };

  // Current Generated Invoice
  generatedInvoice: TransactionResponse | null = null;
  pollingSubscription: Subscription | null = null;

  // UI state variables
  qrisError: string | null = null;
  qrisSuccess: string | null = null;
  qrisLoading = false;
  paymentStatus: string = 'PENDING'; // PENDING, SUCCESS, FAILED

  // Mock list of transactions for merchant log
  transactionLogs: TransactionResponse[] = [];

  ngOnInit(): void {
    this.userId = this.authService.getUserId() || '';
    this.userRole = this.authService.getUserRole() || '';
    this.loadWallet();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  loadWallet(): void {
    this.financeService.getMyWallet().subscribe({
      next: (res) => {
        this.wallet = res;
        this.userStatus = 'ACTIVE';
      },
      error: (err) => {
        console.error('Failed to load wallet', err);
        if (err.status === 403 || err.status === 401) {
          this.userStatus = 'PENDING';
        }
      }
    });
  }

  onGenerateQris(): void {
    if (this.qrisForm.amount <= 0) {
      this.qrisError = 'Jumlah tagihan QRIS harus valid (lebih besar dari 0).';
      return;
    }

    this.qrisLoading = true;
    this.qrisError = null;
    this.qrisSuccess = null;
    this.generatedInvoice = null;
    this.paymentStatus = 'PENDING';

    this.financeService.generateQris(this.qrisForm.amount, this.qrisForm.note).subscribe({
      next: (res) => {
        this.generatedInvoice = res;
        this.qrisLoading = false;
        this.qrisForm = { amount: 0, note: '' };
        
        // Start polling the invoice status
        this.startPolling(res.invoiceId);
      },
      error: (err) => {
        console.error(err);
        this.qrisError = err.error?.message || 'Gagal men-generate QRIS Dinamis.';
        this.qrisLoading = false;
      }
    });
  }

  startPolling(invoiceId: string): void {
    this.stopPolling(); // Clear existing if any

    // RxJS Interval: poll every 3 seconds
    this.pollingSubscription = interval(3000).pipe(
      switchMap(() => this.financeService.getTransactionStatus(invoiceId)),
      // Keep polling while the status is PENDING
      takeWhile(res => res.status === 'PENDING', true)
    ).subscribe({
      next: (res) => {
        this.paymentStatus = res.status;
        if (res.status === 'SUCCESS') {
          this.qrisSuccess = `Pembayaran QRIS senilai Rp${res.amount.toLocaleString()} BERHASIL diterima!`;
          this.transactionLogs.unshift(res); // Add to local log
          this.loadWallet(); // Reload balance
          this.stopPolling();
        } else if (res.status === 'FAILED') {
          this.qrisError = `Pembayaran QRIS gagal: ${res.note || 'Dibatalkan/Gagal'}`;
          this.stopPolling();
        }
      },
      error: (err) => {
        console.error('Polling error', err);
      }
    });
  }

  stopPolling(): void {
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = null;
    }
  }

  clearInvoiceScreen(): void {
    this.generatedInvoice = null;
    this.qrisSuccess = null;
    this.qrisError = null;
    this.paymentStatus = 'PENDING';
  }

  onLogout(): void {
    this.stopPolling();
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
