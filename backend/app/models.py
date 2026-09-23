from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship, sessionmaker

from app.config import settings

engine = create_engine(settings.database_url, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Device(Base):
    __tablename__ = "devices"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    name: Mapped[str] = mapped_column(String(255), default="Android Device")
    device_token: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    device_secret: Mapped[str | None] = mapped_column(String(128), nullable=True)
    pairing_code: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    last_sync_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    paired_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    policies: Mapped[list["Policy"]] = relationship(back_populates="device")


class PairingSession(Base):
    __tablename__ = "pairing_sessions"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    pairing_code: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    device_token: Mapped[str] = mapped_column(String(64), index=True)
    device_name: Mapped[str] = mapped_column(String(255), default="Android Device")
    status: Mapped[str] = mapped_column(String(32), default="PENDING")
    expires_at: Mapped[datetime] = mapped_column(DateTime)
    approved_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    device_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    device_secret: Mapped[str | None] = mapped_column(String(128), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Policy(Base):
    __tablename__ = "policies"
    id: Mapped[str] = mapped_column(String(128), primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"))
    module_type: Mapped[str] = mapped_column(String(64))
    target_type: Mapped[str] = mapped_column(String(64))
    package_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    feature_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    daily_limit_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    block_mode: Mapped[str | None] = mapped_column(String(64), nullable=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    schedule_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    metadata_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    lock_mode: Mapped[str] = mapped_column(String(64), default="NORMAL")
    lock_until_day_end_millis: Mapped[int | None] = mapped_column(Integer, nullable=True)
    lock_until_custom_millis: Mapped[int | None] = mapped_column(Integer, nullable=True)
    lock_on_block_active: Mapped[bool] = mapped_column(Boolean, default=False)
    lock_delay_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    lock_delay_started_at_millis: Mapped[int | None] = mapped_column(Integer, nullable=True)
    source: Mapped[str] = mapped_column(String(64), default="DASHBOARD")
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    applied_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    device: Mapped[Device] = relationship(back_populates="policies")


class InstalledApp(Base):
    __tablename__ = "installed_apps"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"), index=True)
    package_name: Mapped[str] = mapped_column(String(255), index=True)
    label: Mapped[str] = mapped_column(String(255))
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class FeatureControl(Base):
    __tablename__ = "feature_controls"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"), index=True)
    feature_id: Mapped[str] = mapped_column(String(128), index=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    metadata_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    source: Mapped[str] = mapped_column(String(64), default="DASHBOARD")
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    applied_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class DeviceChangeEvent(Base):
    __tablename__ = "device_change_events"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"), index=True)
    event_type: Mapped[str] = mapped_column(String(64))
    summary: Mapped[str] = mapped_column(String(255))
    detail_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class UsageSnapshot(Base):
    __tablename__ = "usage_snapshots"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"))
    date_key: Mapped[str] = mapped_column(String(16), index=True)
    package_name: Mapped[str] = mapped_column(String(255))
    used_millis: Mapped[int] = mapped_column(Integer)
    synced_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class UnlockGrant(Base):
    __tablename__ = "unlock_grants"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"))
    package_name: Mapped[str] = mapped_column(String(255))
    source: Mapped[str] = mapped_column(String(64))
    granted_until: Mapped[datetime] = mapped_column(DateTime)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class OverrideEvent(Base):
    __tablename__ = "override_events"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"))
    override_type: Mapped[str] = mapped_column(String(64))
    active: Mapped[bool] = mapped_column(Boolean)
    until: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


def _ensure_sqlite_columns() -> None:
    if engine.dialect.name != "sqlite":
        return

    columns_to_add = {
        "block_mode": "VARCHAR(64)",
        "lock_mode": "VARCHAR(64) DEFAULT 'NORMAL'",
        "lock_until_day_end_millis": "INTEGER",
        "lock_until_custom_millis": "INTEGER",
        "lock_on_block_active": "BOOLEAN DEFAULT 0",
        "lock_delay_minutes": "INTEGER",
        "lock_delay_started_at_millis": "INTEGER",
        "source": "VARCHAR(64) DEFAULT 'DASHBOARD'",
        "updated_at": "DATETIME",
        "applied_at": "DATETIME",
    }

    device_columns_to_add = {
        "device_secret": "VARCHAR(128)",
        "paired_at": "DATETIME",
    }

    with engine.begin() as connection:
        existing_policy_columns = {
            row[1]
            for row in connection.exec_driver_sql("PRAGMA table_info(policies)").fetchall()
        }
        for column_name, column_type in columns_to_add.items():
            if column_name not in existing_policy_columns:
                connection.exec_driver_sql(
                    f"ALTER TABLE policies ADD COLUMN {column_name} {column_type}"
                )

        if "updated_at" not in existing_policy_columns:
            connection.exec_driver_sql(
                "UPDATE policies SET updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP)"
            )

        existing_device_columns = {
            row[1]
            for row in connection.exec_driver_sql("PRAGMA table_info(devices)").fetchall()
        }
        for column_name, column_type in device_columns_to_add.items():
            if column_name not in existing_device_columns:
                connection.exec_driver_sql(
                    f"ALTER TABLE devices ADD COLUMN {column_name} {column_type}"
                )


def init_db() -> None:
    Base.metadata.create_all(bind=engine)
    _ensure_sqlite_columns()
