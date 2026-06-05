import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { RegisterMerchantComponent } from './components/register-merchant/register-merchant.component';
import { UserDashboardComponent } from './components/user-dashboard/user-dashboard.component';
import { MerchantDashboardComponent } from './components/merchant-dashboard/merchant-dashboard.component';
import { AdminDashboardComponent } from './components/admin-dashboard/admin-dashboard.component';
import { SupportComplaintsComponent } from './components/support-complaints/support-complaints.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'register-merchant', component: RegisterMerchantComponent },
  
  { 
    path: 'dashboard/user', 
    component: UserDashboardComponent, 
    canActivate: [authGuard], 
    data: { role: 'USER' } 
  },
  { 
    path: 'dashboard/merchant', 
    component: MerchantDashboardComponent, 
    canActivate: [authGuard], 
    data: { role: 'MERCHANT' } 
  },
  { 
    path: 'dashboard/admin', 
    component: AdminDashboardComponent, 
    canActivate: [authGuard], 
    data: { role: 'ADMIN' } 
  },
  { 
    path: 'support', 
    component: SupportComplaintsComponent, 
    canActivate: [authGuard] 
  },
  
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
