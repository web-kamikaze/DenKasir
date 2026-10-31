"""Initial database schema for DenKasir POS & BKU

Revision ID: 001_initial_schema
Revises: 
Create Date: 2026-09-05 09:15:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

revision: str = '001_initial_schema'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. Stores Table
    op.create_table(
        'stores',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('name', sa.String(length=200), nullable=False),
        sa.Column('address', sa.String(length=500), nullable=True),
        sa.Column('phone', sa.String(length=50), nullable=True),
        sa.Column('owner_id', sa.String(length=36), nullable=False),
        sa.Column('is_active', sa.Boolean(), nullable=False, default=True),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('updated_at', sa.BigInteger(), nullable=False),
        sa.Column('sync_status', sa.String(length=20), nullable=False, default='SYNCED')
    )
    op.create_index('idx_stores_owner', 'stores', ['owner_id'])
    op.create_index('idx_stores_updated_at', 'stores', ['updated_at'])

    # 2. Users Table
    op.create_table(
        'users',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False),
        sa.Column('username', sa.String(length=100), nullable=False, unique=True),
        sa.Column('password_hash', sa.String(length=255), nullable=False),
        sa.Column('full_name', sa.String(length=200), nullable=False),
        sa.Column('role', sa.String(length=20), nullable=False, default='KASIR'),
        sa.Column('is_active', sa.Boolean(), nullable=False, default=True),
        sa.Column('created_at', sa.BigInteger(), nullable=False)
    )
    op.create_index('idx_users_store_role', 'users', ['store_id', 'role'])

    # 3. Products Table
    op.create_table(
        'products',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('barcode', sa.String(length=100), nullable=True),
        sa.Column('name', sa.String(length=255), nullable=False),
        sa.Column('category', sa.String(length=100), nullable=False, default='Pcs'),
        sa.Column('product_type', sa.String(length=50), nullable=False, default='RETAIL'),
        sa.Column('cost_price', sa.Float(), nullable=False, default=0.0),
        sa.Column('selling_price', sa.Float(), nullable=False, default=0.0),
        sa.Column('stock', sa.Float(), nullable=False, default=0.0),
        sa.Column('min_stock_alert', sa.Float(), nullable=False, default=5.0),
        sa.Column('image_path', sa.String(length=500), nullable=True),
        sa.Column('provider_code', sa.String(length=100), nullable=True),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('updated_at', sa.BigInteger(), nullable=False),
        sa.Column('deleted_at', sa.BigInteger(), nullable=True),
        sa.Column('sync_status', sa.String(length=20), nullable=False, default='SYNCED')
    )
    op.create_index('idx_products_store_category', 'products', ['store_id', 'category'])
    op.create_index('idx_products_store_barcode', 'products', ['store_id', 'barcode'])
    op.create_index('idx_products_updated_at', 'products', ['updated_at'])

    # 4. Transactions Table
    op.create_table(
        'transactions',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('user_id', sa.String(length=36), nullable=False, default='user_1'),
        sa.Column('invoice_number', sa.String(length=100), nullable=False),
        sa.Column('total_amount', sa.Float(), nullable=False, default=0.0),
        sa.Column('cash_given', sa.Float(), nullable=False, default=0.0),
        sa.Column('change_amount', sa.Float(), nullable=False, default=0.0),
        sa.Column('payment_method', sa.String(length=50), nullable=False, default='CASH'),
        sa.Column('customer_name', sa.String(length=200), nullable=True),
        sa.Column('status', sa.String(length=50), nullable=False, default='COMPLETED'),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('updated_at', sa.BigInteger(), nullable=False),
        sa.Column('deleted_at', sa.BigInteger(), nullable=True),
        sa.Column('sync_status', sa.String(length=20), nullable=False, default='SYNCED')
    )
    op.create_index('idx_transactions_store_created', 'transactions', ['store_id', 'created_at'])
    op.create_index('idx_transactions_store_invoice', 'transactions', ['store_id', 'invoice_number'])

    # 5. Transaction Items Table
    op.create_table(
        'transaction_items',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('transaction_id', sa.String(length=36), sa.ForeignKey('transactions.id', ondelete='CASCADE'), nullable=False),
        sa.Column('product_id', sa.String(length=36), nullable=False),
        sa.Column('product_name', sa.String(length=255), nullable=False),
        sa.Column('quantity', sa.Float(), nullable=False, default=1.0),
        sa.Column('cost_price', sa.Float(), nullable=False, default=0.0),
        sa.Column('selling_price', sa.Float(), nullable=False, default=0.0),
        sa.Column('subtotal', sa.Float(), nullable=False, default=0.0),
        sa.Column('target_phone_number', sa.String(length=50), nullable=True),
        sa.Column('sn_number', sa.String(length=100), nullable=True),
        sa.Column('digital_status', sa.String(length=50), nullable=False, default='SUCCESS')
    )
    op.create_index('idx_trx_items_trx_id', 'transaction_items', ['transaction_id'])

    # 6. Cash Flows Table
    op.create_table(
        'cash_flows',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('flow_type', sa.String(length=10), nullable=False),
        sa.Column('category', sa.String(length=100), nullable=False),
        sa.Column('amount', sa.Float(), nullable=False, default=0.0),
        sa.Column('description', sa.String(length=500), nullable=False),
        sa.Column('reference_id', sa.String(length=100), nullable=True),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('updated_at', sa.BigInteger(), nullable=False),
        sa.Column('deleted_at', sa.BigInteger(), nullable=True),
        sa.Column('sync_status', sa.String(length=20), nullable=False, default='SYNCED')
    )
    op.create_index('idx_cash_flows_store_flow', 'cash_flows', ['store_id', 'flow_type'])
    op.create_index('idx_cash_flows_store_created', 'cash_flows', ['store_id', 'created_at'])

    # 7. BKU Accounts Table
    op.create_table(
        'bku_accounts',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('account_name', sa.String(length=200), nullable=False),
        sa.Column('account_type', sa.String(length=10), nullable=False),
        sa.Column('account_code', sa.String(length=50), nullable=True),
        sa.Column('is_default', sa.Boolean(), nullable=False, default=False),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('updated_at', sa.BigInteger(), nullable=False)
    )

    # 8. Debts Table
    op.create_table(
        'debts',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('customer_name', sa.String(length=200), nullable=False),
        sa.Column('customer_phone', sa.String(length=50), nullable=True),
        sa.Column('total_debt', sa.Float(), nullable=False, default=0.0),
        sa.Column('paid_amount', sa.Float(), nullable=False, default=0.0),
        sa.Column('status', sa.String(length=20), nullable=False, default='UNPAID'),
        sa.Column('debt_type', sa.String(length=20), nullable=False, default='PIUTANG'),
        sa.Column('due_date', sa.BigInteger(), nullable=True),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('updated_at', sa.BigInteger(), nullable=False),
        sa.Column('deleted_at', sa.BigInteger(), nullable=True),
        sa.Column('sync_status', sa.String(length=20), nullable=False, default='SYNCED')
    )
    op.create_index('idx_debts_store_status', 'debts', ['store_id', 'status'])
    op.create_index('idx_debts_store_type', 'debts', ['store_id', 'debt_type'])

    # 9. Stock Opnames Table
    op.create_table(
        'stock_opnames',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('product_id', sa.String(length=36), nullable=False),
        sa.Column('system_stock', sa.Float(), nullable=False, default=0.0),
        sa.Column('physical_stock', sa.Float(), nullable=False, default=0.0),
        sa.Column('difference', sa.Float(), nullable=False, default=0.0),
        sa.Column('reason', sa.String(length=500), nullable=True),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('sync_status', sa.String(length=20), nullable=False, default='SYNCED')
    )

    # 10. Digital Configs Table
    op.create_table(
        'digital_configs',
        sa.Column('id', sa.String(length=36), primary_key=True, default='default_config'),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('provider_name', sa.String(length=100), nullable=False, default='OtomaX H2H'),
        sa.Column('api_url', sa.String(length=500), nullable=False),
        sa.Column('api_key', sa.String(length=255), nullable=False, default=''),
        sa.Column('member_id', sa.String(length=100), nullable=False, default=''),
        sa.Column('pin_password', sa.String(length=100), nullable=False, default=''),
        sa.Column('balance', sa.Float(), nullable=False, default=0.0),
        sa.Column('is_active', sa.Boolean(), nullable=False, default=True),
        sa.Column('updated_at', sa.BigInteger(), nullable=False)
    )

    # 11. Purchase Orders Table
    op.create_table(
        'purchase_orders',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False, default='default_store'),
        sa.Column('supplier_name', sa.String(length=200), nullable=False),
        sa.Column('date_text', sa.String(length=50), nullable=False),
        sa.Column('item_count', sa.Integer(), nullable=False, default=0),
        sa.Column('total_amount', sa.Float(), nullable=False, default=0.0),
        sa.Column('status', sa.String(length=50), nullable=False, default='Draft'),
        sa.Column('created_at', sa.BigInteger(), nullable=False),
        sa.Column('updated_at', sa.BigInteger(), nullable=False)
    )

    # 12. Sync Logs Table
    op.create_table(
        'sync_logs',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('store_id', sa.String(length=36), nullable=False),
        sa.Column('user_id', sa.String(length=36), nullable=False),
        sa.Column('sync_direction', sa.String(length=20), nullable=False),
        sa.Column('status', sa.String(length=20), nullable=False),
        sa.Column('records_synced', sa.Integer(), nullable=False, default=0),
        sa.Column('client_timestamp', sa.BigInteger(), nullable=True),
        sa.Column('server_timestamp', sa.BigInteger(), nullable=False),
        sa.Column('details', sa.Text(), nullable=True)
    )
    op.create_index('idx_sync_logs_store_server_ts', 'sync_logs', ['store_id', 'server_timestamp'])


def downgrade() -> None:
    op.drop_table('sync_logs')
    op.drop_table('purchase_orders')
    op.drop_table('digital_configs')
    op.drop_table('stock_opnames')
    op.drop_table('debts')
    op.drop_table('bku_accounts')
    op.drop_table('cash_flows')
    op.drop_table('transaction_items')
    op.drop_table('transactions')
    op.drop_table('products')
    op.drop_table('users')
    op.drop_table('stores')
