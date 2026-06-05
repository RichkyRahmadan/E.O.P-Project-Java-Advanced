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

  // Direct actions input values
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
  }

  onVerifyKyc(): void {
    if (!this.targetUserIdKyc.trim()) {
      this.kycError = 'User ID tidak boleh kosong.';
      return;
    }

    this.kycLoading = true;
    this.kycError = null;
    this.kycSuccess = null;

    this.http.patch(`/api/admin/users/${this.targetUserIdKyc.trim()}/kyc`, null, { responseType: 'text' }).subscribe({
      next: (res) => {
        this.kycSuccess = res;
        this.auditLogs.unshift({
          action: 'VERIFY KYC',
          target: `User ID: ${this.targetUserIdKyc}`,
          time: new Date()
        });
        this.targetUserIdKyc = '';
        this.kycLoading = false;
      },
      error: (err) => {
        console.error(err);
        this.kycError = err.error || 'Gagal memverifikasi KYC. Pastikan UUID valid dan Anda memiliki hak akses.';
        this.kycLoading = false;
      }
    });
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
      },
      error: (err) => {
        console.error(err);
        this.suspendError = err.error || 'Gagal membekukan akun. Pastikan UUID valid dan Anda memiliki hak akses.';
        this.suspendLoading = false;
      }
    });
  }

  onVerifyMerchant(): void {
    if (!this.targetMerchantIdVerify.trim()) {
      this.merchantError = 'Merchant ID tidak boleh kosong.';
      return;
    }

    this.merchantLoading = true;
    this.merchantError = null;
    this.merchantSuccess = null;

    this.http.patch(`/api/admin/merchants/${this.targetMerchantIdVerify.trim()}/verify`, null, { responseType: 'text' }).subscribe({
      next: (res) => {
        this.merchantSuccess = res;
        this.auditLogs.unshift({
          action: 'VERIFY MERCHANT',
          target: `Merchant ID: ${this.targetMerchantIdVerify}`,
          time: new Date()
        });
        this.targetMerchantIdVerify = '';
        this.merchantLoading = false;
      },
      error: (err) => {
        console.error(err);
        this.merchantError = err.error || 'Gagal memverifikasi merchant. Pastikan UUID valid dan Anda memiliki hak akses.';
        this.merchantLoading = false;
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
