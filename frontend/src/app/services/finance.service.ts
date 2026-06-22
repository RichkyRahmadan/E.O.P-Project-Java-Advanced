import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface WalletResponse {
  id: string;
  ownerId: string;
  ownerType: string;
  balance: number;
  createdAt: string;
}

export interface TransactionResponse {
  invoiceId: string;
  transactionType: string;
  status: string;
  amount: number;
  sender: any;
  recipient: any;
  rawQrisData?: string;
  note?: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class FinanceService {
  private http = inject(HttpClient);

  getMyWallet(): Observable<WalletResponse> {
    return this.http.get<WalletResponse>('/api/finance/wallet');
  }

  transfer(recipientWalletId: string, amount: number, note: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/finance/transfer', {
      recipientWalletId,
      amount,
      note
    });
  }

  generateQris(amount: number, note: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/finance/qris/generate', {
      amount,
      note
    });
  }

  payQris(invoiceId: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/finance/qris/pay', {
      invoiceId
    });
  }

  redeemVoucher(code: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/finance/voucher/redeem', {
      code
    });
  }

  transferToOwner(amount: number, note: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/finance/transfer/to-owner', {
      amount,
      note
    });
  }

  getTransactionStatus(invoiceId: string): Observable<TransactionResponse> {
    return this.http.get<TransactionResponse>(`/api/finance/transactions/${invoiceId}`);
  }
}
