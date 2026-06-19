import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register-merchant',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register-merchant.component.html',
  styleUrls: ['./register-merchant.component.css']
})
export class RegisterMerchantComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  merchantData = {
    username: '',
    email: '',
    password: '',
    merchantName: '',
    address: ''
  };

  errorMessage: string | null = null;
  successMessage: string | null = null;
  loading = false;

  onSubmit(): void {
    if (
      !this.merchantData.username || 
      !this.merchantData.email || 
      !this.merchantData.password || 
      !this.merchantData.merchantName || 
      !this.merchantData.address
    ) {
      this.errorMessage = 'Semua field wajib diisi.';
      return;
    }

    if (this.merchantData.password.length < 8) {
      this.errorMessage = 'Password minimal terdiri dari 8 karakter.';
      return;
    }

    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;

    this.authService.registerMerchant(this.merchantData).subscribe({
      next: (res) => {
        this.loading = false;
        this.successMessage = res.message || 'Registrasi Merchant berhasil. Silakan hubungi admin untuk melakukan verifikasi akun.';
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 5000);
      },
      error: (err) => {
        console.error(err);
        this.errorMessage = err.error?.message || 'Registrasi Merchant gagal. Silakan coba username atau email lain.';
        this.loading = false;
      }
    });
  }
}
