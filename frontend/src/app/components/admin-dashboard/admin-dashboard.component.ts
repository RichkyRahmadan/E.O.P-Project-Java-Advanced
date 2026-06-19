import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);
  private http = inject(HttpClient);

  // Admin details
  userId = '';
  username = '';
  userRole = '';

  // Pending Lists
  pendingUsers: any[] = [];
  pendingMerchants: any[] = [];
  usersLoading = false;
  merchantsLoading = false;

  // Direct actions input values (optional fallbacks)
  targetUserIdKyc = '';
  targetUserIdSuspend = '';
  targetMerchantIdVerify = '';

  // Messages & Loadings
  kycSuccess: string | null = null;
  kycError: string | null = null;
  kycLoading = false;

  suspendSuccess: string | null = null;
  suspendError: string | null = null;
  suspendLoading = false;

  merchantSuccess: string | null = null;
  merchantError: string | null = null;
  merchantLoading = false;

  // Audit Logs (local session)
  auditLogs: Array<{ action: string; target: string; time: Date }> = [];

  ngOnInit(): void {
    this.userId = this.authService.getUserId() || '';
    this.userRole = this.authService.getUserRole() || '';
    this.loadPendingUsers();
    this.loadPendingMerchants();
  }

  loadPendingUsers(): void {
    this.usersLoading = true;
    this.http.get<any[]>('/api/admin/users/pending').subscribe({
      next: (data) => {
        this.pendingUsers = data;
        this.usersLoading = false;
      },
      error: (err) => {
        console.error('Gagal mengambil daftar user pending:', err);
        this.usersLoading = false;
      }
    });
  }

  loadPendingMerchants(): void {
    this.merchantsLoading = true;
    this.http.get<any[]>('/api/admin/merchants/pending').subscribe({
      next: (data) => {
        this.pendingMerchants = data;
        this.merchantsLoading = false;
      },
      error: (err) => {
        console.error('Gagal mengambil daftar merchant pending:', err);
        this.merchantsLoading = false;
      }
    });
  }

  verifyKycDirect(userId: string): void {
    this.kycLoading = true;
    this.kycError = null;
    this.kycSuccess = null;

    this.http.patch(`/api/admin/users/${userId}/kyc`, null, { responseType: 'text' }).subscribe({
      next: (res) => {
        this.kycSuccess = res;
        this.auditLogs.unshift({
          action: 'VERIFY KYC',
          target: `User ID: ${userId}`,
          time: new Date()
        });
        this.kycLoading = false;
        this.loadPendingUsers();
      },
      error: (err) => {
        console.error(err);
        this.kycError = err.error || 'Gagal memverifikasi KYC.';
        this.kycLoading = false;
      }
    });
  }

  verifyMerchantDirect(merchantId: string): void {
    this.merchantLoading = true;
    this.merchantError = null;
    this.merchantSuccess = null;

    this.http.patch(`/api/admin/merchants/${merchantId}/verify`, null, { responseType: 'text' }).subscribe({
      next: (res) => {
        this.merchantSuccess = res;
        this.auditLogs.unshift({
          action: 'VERIFY MERCHANT',
          target: `Merchant ID: ${merchantId}`,
          time: new Date()
        });
        this.merchantLoading = false;
        this.loadPendingMerchants();
      },
      error: (err) => {
        console.error(err);
        this.merchantError = err.error || 'Gagal memverifikasi merchant.';
        this.merchantLoading = false;
      }
    });
  }

  onVerifyKyc(): void {
    if (!this.targetUserIdKyc.trim()) {
      this.kycError = 'User ID tidak boleh kosong.';
      return;
    }
    this.verifyKycDirect(this.targetUserIdKyc.trim());
    this.targetUserIdKyc = '';
  }

  onVerifyMerchant(): void {
    if (!this.targetMerchantIdVerify.trim()) {
      this.merchantError = 'Merchant ID tidak boleh kosong.';
      return;
    }
    this.verifyMerchantDirect(this.targetMerchantIdVerify.trim());
    this.targetMerchantIdVerify = '';
  }

  onSuspendUser(): void {
    if (!this.targetUserIdSuspend.trim()) {
      this.suspendError = 'User ID tidak boleh kosong.';
      return;
    }

    this.suspendLoading = true;
    this.suspendError = null;
    this.suspendSuccess = null;

    this.http.patch(`/api/admin/users/${this.targetUserIdSuspend.trim()}/suspend`, null, { responseType: 'text' }).subscribe({
      next: (res) => {
        this.suspendSuccess = res;
        this.auditLogs.unshift({
          action: 'SUSPEND USER',
          target: `User ID: ${this.targetUserIdSuspend}`,
          time: new Date()
        });
        this.targetUserIdSuspend = '';
        this.suspendLoading = false;
        this.loadPendingUsers(); // Refresh in case they were pending
      },
      error: (err) => {
        console.error(err);
        this.suspendError = err.error || 'Gagal membekukan akun. Pastikan UUID valid dan Anda memiliki hak akses.';
        this.suspendLoading = false;
      }
    });
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
