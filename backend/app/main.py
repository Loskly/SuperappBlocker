from datetime import datetime, timedelta
import ipaddress
import json
import secrets
import socket
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from jose import JWTError, jwt
from passlib.context import CryptContext
from pydantic import AliasChoices, BaseModel, ConfigDict, EmailStr, Field
from sqlalchemy.orm import Session

from app.config import settings
from app.models import (
    Device,
    DeviceChangeEvent,
    FeatureControl,
    InstalledApp,
    OverrideEvent,
    PairingSession,
    Policy,
    SessionLocal,
    UnlockGrant,
    UsageSnapshot,
    User,
    init_db,
)

app = FastAPI(title=settings.app_name)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")


class ApiModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")


class TokenResponse(ApiModel):
    access_token: str
    token_type: str = "bearer"


class RegisterRequest(ApiModel):
    email: EmailStr
    password: str


class DeviceRegisterRequest(ApiModel):
    name: str = "Android Device"
    pairing_code: str = Field(
        validation_alias=AliasChoices("pairingCode", "pairing_code"),
        serialization_alias="pairingCode",
    )


class DeviceResponse(ApiModel):
    id: int
    name: str
    pairing_code: str = Field(serialization_alias="pairingCode")
    last_sync_at: datetime | None = Field(default=None, serialization_alias="lastSyncAt")
    paired_at: datetime | None = Field(default=None, serialization_alias="pairedAt")


class PairingStartRequest(ApiModel):
    device_token: str = Field(
        validation_alias=AliasChoices("deviceToken", "device_token"),
        serialization_alias="deviceToken",
    )
    device_name: str = Field(
        default="Android Device",
        validation_alias=AliasChoices("deviceName", "device_name"),
        serialization_alias="deviceName",
    )


class PairingStartResponse(ApiModel):
    pairing_code: str = Field(serialization_alias="pairingCode")
    expires_at: datetime = Field(serialization_alias="expiresAt")
    status: str


class PairingStatusResponse(ApiModel):
    status: str
    pairing_code: str | None = Field(default=None, serialization_alias="pairingCode")
    expires_at: datetime | None = Field(default=None, serialization_alias="expiresAt")
    device_id: int | None = Field(default=None, serialization_alias="deviceId")
    device_secret: str | None = Field(default=None, serialization_alias="deviceSecret")


class PolicyItem(ApiModel):
    id: str
    module_type: str = Field(
        validation_alias=AliasChoices("moduleType", "module_type"),
        serialization_alias="moduleType",
    )
    target_type: str = Field(
        validation_alias=AliasChoices("targetType", "target_type"),
        serialization_alias="targetType",
    )
    package_name: str | None = Field(
        default=None,
        validation_alias=AliasChoices("packageName", "package_name"),
        serialization_alias="packageName",
    )
    feature_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("featureId", "feature_id"),
        serialization_alias="featureId",
    )
    daily_limit_minutes: int | None = Field(
        default=None,
        validation_alias=AliasChoices("dailyLimitMinutes", "daily_limit_minutes"),
        serialization_alias="dailyLimitMinutes",
    )
    block_mode: str | None = Field(
        default=None,
        validation_alias=AliasChoices("blockMode", "block_mode"),
        serialization_alias="blockMode",
    )
    enabled: bool = True
    schedule_json: str | None = Field(
        default=None,
        validation_alias=AliasChoices("scheduleJson", "schedule_json"),
        serialization_alias="scheduleJson",
    )
    metadata_json: str | None = Field(
        default=None,
        validation_alias=AliasChoices("metadataJson", "metadata_json"),
        serialization_alias="metadataJson",
    )
    lock_mode: str = Field(
        default="NORMAL",
        validation_alias=AliasChoices("lockMode", "lock_mode"),
        serialization_alias="lockMode",
    )
    lock_until_day_end_millis: int | None = Field(
        default=None,
        validation_alias=AliasChoices("lockUntilDayEndMillis", "lock_until_day_end_millis"),
        serialization_alias="lockUntilDayEndMillis",
    )
    lock_until_custom_millis: int | None = Field(
        default=None,
        validation_alias=AliasChoices("lockUntilCustomMillis", "lock_until_custom_millis"),
        serialization_alias="lockUntilCustomMillis",
    )
    lock_on_block_active: bool = Field(
        default=False,
        validation_alias=AliasChoices("lockOnBlockActive", "lock_on_block_active"),
        serialization_alias="lockOnBlockActive",
    )
    lock_delay_minutes: int | None = Field(
        default=None,
        validation_alias=AliasChoices("lockDelayMinutes", "lock_delay_minutes"),
        serialization_alias="lockDelayMinutes",
    )
    lock_delay_started_at_millis: int | None = Field(
        default=None,
        validation_alias=AliasChoices("lockDelayStartedAtMillis", "lock_delay_started_at_millis"),
        serialization_alias="lockDelayStartedAtMillis",
    )
    source: str = "DASHBOARD"
    updated_at: datetime | None = Field(
        default=None,
        validation_alias=AliasChoices("updatedAt", "updated_at"),
        serialization_alias="updatedAt",
    )
    applied_at: datetime | None = Field(
        default=None,
        validation_alias=AliasChoices("appliedAt", "applied_at"),
        serialization_alias="appliedAt",
    )


class PoliciesUpdateRequest(ApiModel):
    policies: list[PolicyItem]


class FeatureControlItem(ApiModel):
    feature_id: str = Field(
        validation_alias=AliasChoices("featureId", "feature_id"),
        serialization_alias="featureId",
    )
    enabled: bool = False
    metadata_json: str | None = Field(
        default=None,
        validation_alias=AliasChoices("metadataJson", "metadata_json"),
        serialization_alias="metadataJson",
    )
    source: str = "DASHBOARD"
    updated_at: datetime | None = Field(
        default=None,
        validation_alias=AliasChoices("updatedAt", "updated_at"),
        serialization_alias="updatedAt",
    )
    applied_at: datetime | None = Field(
        default=None,
        validation_alias=AliasChoices("appliedAt", "applied_at"),
        serialization_alias="appliedAt",
    )


class FeatureControlsUpdateRequest(ApiModel):
    features: list[FeatureControlItem]


class InstalledAppItem(ApiModel):
    package_name: str = Field(
        validation_alias=AliasChoices("packageName", "package_name"),
        serialization_alias="packageName",
    )
    label: str


class DeviceStateSyncRequest(ApiModel):
    device_token: str = Field(
        validation_alias=AliasChoices("deviceToken", "device_token"),
        serialization_alias="deviceToken",
    )
    installed_apps: list[InstalledAppItem] = Field(
        default_factory=list,
        validation_alias=AliasChoices("installedApps", "installed_apps"),
        serialization_alias="installedApps",
    )
    policies: list[PolicyItem] = Field(default_factory=list)
    features: list[FeatureControlItem] = Field(default_factory=list)


class DeviceChangeResponse(ApiModel):
    id: int
    event_type: str = Field(serialization_alias="eventType")
    summary: str
    detail_json: str | None = Field(default=None, serialization_alias="detailJson")
    created_at: datetime = Field(serialization_alias="createdAt")


class UsageEntry(ApiModel):
    package_name: str = Field(
        validation_alias=AliasChoices("packageName", "package_name"),
        serialization_alias="packageName",
    )
    used_millis: int = Field(
        validation_alias=AliasChoices("usedMillis", "used_millis"),
        serialization_alias="usedMillis",
    )


class UsageSyncRequest(ApiModel):
    device_token: str = Field(
        validation_alias=AliasChoices("deviceToken", "device_token"),
        serialization_alias="deviceToken",
    )
    date_key: str = Field(
        validation_alias=AliasChoices("dateKey", "date_key"),
        serialization_alias="dateKey",
    )
    entries: list[UsageEntry]


class UsageSnapshotResponse(ApiModel):
    date_key: str = Field(serialization_alias="dateKey")
    package_name: str = Field(serialization_alias="packageName")
    used_millis: int = Field(serialization_alias="usedMillis")
    synced_at: datetime = Field(serialization_alias="syncedAt")


class UnlockRequest(ApiModel):
    package_name: str = Field(
        validation_alias=AliasChoices("packageName", "package_name"),
        serialization_alias="packageName",
    )
    duration_minutes: int = Field(
        default=15,
        validation_alias=AliasChoices("durationMinutes", "duration_minutes"),
        serialization_alias="durationMinutes",
    )


class UnlockResponse(ApiModel):
    granted_until: datetime = Field(serialization_alias="grantedUntil")


class DeviceStatusResponse(ApiModel):
    device_id: int = Field(serialization_alias="deviceId")
    online: bool
    last_sync_at: datetime | None = Field(serialization_alias="lastSyncAt")


class DashboardConnectionInfoResponse(ApiModel):
    local_url: str = Field(serialization_alias="localUrl")
    recommended_phone_url: str | None = Field(default=None, serialization_alias="recommendedPhoneUrl")
    phone_urls: list[str] = Field(default_factory=list, serialization_alias="phoneUrls")


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def create_access_token(subject: str) -> str:
    expire = datetime.utcnow() + timedelta(minutes=settings.access_token_expire_minutes)
    return jwt.encode({"sub": subject, "exp": expire}, settings.secret_key, algorithm="HS256")


def get_or_create_local_dashboard_user(db: Session) -> User:
    email = "local@appbllocker.dev"
    user = db.query(User).filter(User.email == email).first()
    if user:
        return user
    user = User(email=email, password_hash="local-dashboard-login-only")
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def get_current_user(token: Annotated[str, Depends(oauth2_scheme)], db: Annotated[Session, Depends(get_db)]) -> User:
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
        email = payload.get("sub")
    except JWTError as exc:
        raise HTTPException(status_code=401, detail="Invalid token") from exc
    user = db.query(User).filter(User.email == email).first()
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return user


def get_device_for_user(db: Session, user: User, device_id: int) -> Device:
    device = db.query(Device).filter(Device.id == device_id, Device.user_id == user.id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    return device


def get_device_by_token(db: Session, device_token: str, device_secret: str | None = None) -> Device:
    device = db.query(Device).filter(Device.device_token == device_token).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    if device.device_secret and device.device_secret != device_secret:
        raise HTTPException(status_code=401, detail="Invalid device secret")
    return device


def format_pairing_code(code: str) -> str:
    normalized = code.replace("-", "").upper()
    return "-".join([normalized[i : i + 4] for i in range(0, len(normalized), 4)])


def normalize_pairing_code(code: str) -> str:
    return code.replace("-", "").replace(" ", "").upper()


def generate_pairing_code(db: Session) -> str:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    for _ in range(20):
        code = "".join(secrets.choice(alphabet) for _ in range(8))
        session_exists = db.query(PairingSession).filter(PairingSession.pairing_code == code).first()
        device_exists = db.query(Device).filter(Device.pairing_code == code).first()
        if not session_exists and not device_exists:
            return code
    raise HTTPException(status_code=500, detail="Unable to generate pairing code")


def generate_device_secret() -> str:
    return secrets.token_urlsafe(32)


def detect_phone_backend_urls(port: int) -> list[str]:
    addresses: list[str] = []

    def add_address(value: str | None) -> None:
        if not value:
            return
        try:
            address = ipaddress.ip_address(value)
        except ValueError:
            return
        if (
            address.version != 4
            or address.is_loopback
            or address.is_link_local
            or address.is_multicast
            or str(address) == "0.0.0.0"
        ):
            return
        text = str(address)
        if text not in addresses:
            addresses.append(text)

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.connect(("8.8.8.8", 80))
            add_address(probe.getsockname()[0])
    except OSError:
        pass

    try:
        hostname = socket.gethostname()
        for candidate in socket.gethostbyname_ex(hostname)[2]:
            add_address(candidate)
    except OSError:
        pass

    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            add_address(info[4][0])
    except OSError:
        pass

    def sort_key(value: str) -> tuple[int, str]:
        address = ipaddress.ip_address(value)
        return (0 if address.is_private else 1, value)

    return [f"http://{address}:{port}" for address in sorted(addresses, key=sort_key)]


def policy_to_item(policy: Policy) -> PolicyItem:
    return PolicyItem(
        id=policy.id,
        module_type=policy.module_type,
        target_type=policy.target_type,
        package_name=policy.package_name,
        feature_id=policy.feature_id,
        daily_limit_minutes=policy.daily_limit_minutes,
        block_mode=policy.block_mode,
        enabled=policy.enabled,
        schedule_json=policy.schedule_json,
        metadata_json=policy.metadata_json,
        lock_mode=policy.lock_mode or "NORMAL",
        lock_until_day_end_millis=policy.lock_until_day_end_millis,
        lock_until_custom_millis=policy.lock_until_custom_millis,
        lock_on_block_active=bool(policy.lock_on_block_active),
        lock_delay_minutes=policy.lock_delay_minutes,
        lock_delay_started_at_millis=policy.lock_delay_started_at_millis,
        source=policy.source or "DASHBOARD",
        updated_at=policy.updated_at,
        applied_at=policy.applied_at,
    )


def apply_policy_item(policy: Policy, item: PolicyItem, source: str | None = None) -> None:
    policy.module_type = item.module_type
    policy.target_type = item.target_type
    policy.package_name = item.package_name
    policy.feature_id = item.feature_id
    policy.daily_limit_minutes = item.daily_limit_minutes
    policy.block_mode = item.block_mode
    policy.enabled = item.enabled
    policy.schedule_json = item.schedule_json
    policy.metadata_json = item.metadata_json
    policy.lock_mode = item.lock_mode
    policy.lock_until_day_end_millis = item.lock_until_day_end_millis
    policy.lock_until_custom_millis = item.lock_until_custom_millis
    policy.lock_on_block_active = item.lock_on_block_active
    policy.lock_delay_minutes = item.lock_delay_minutes
    policy.lock_delay_started_at_millis = item.lock_delay_started_at_millis
    if source:
        policy.source = source


def policy_differs_from_item(policy: Policy, item: PolicyItem, source: str | None = None) -> bool:
    expected_source = source or policy.source or item.source
    comparisons = [
        (policy.module_type, item.module_type),
        (policy.target_type, item.target_type),
        (policy.package_name, item.package_name),
        (policy.feature_id, item.feature_id),
        (policy.daily_limit_minutes, item.daily_limit_minutes),
        (policy.block_mode, item.block_mode),
        (policy.enabled, item.enabled),
        (policy.schedule_json, item.schedule_json),
        (policy.metadata_json, item.metadata_json),
        (policy.lock_mode, item.lock_mode),
        (policy.lock_until_day_end_millis, item.lock_until_day_end_millis),
        (policy.lock_until_custom_millis, item.lock_until_custom_millis),
        (bool(policy.lock_on_block_active), bool(item.lock_on_block_active)),
        (policy.lock_delay_minutes, item.lock_delay_minutes),
        (policy.lock_delay_started_at_millis, item.lock_delay_started_at_millis),
        (policy.source, expected_source),
    ]
    return any(current != expected for current, expected in comparisons)


def upsert_policy_for_device(
    db: Session,
    device: Device,
    item: PolicyItem,
    source: str | None,
    updated_at: datetime,
    applied_at: datetime | None = None,
) -> Policy:
    policy = (
        db.query(Policy)
        .filter(Policy.device_id == device.id, Policy.id == item.id)
        .first()
    )
    if not policy:
        policy = Policy(
            id=item.id,
            device_id=device.id,
            module_type=item.module_type,
            target_type=item.target_type,
            package_name=item.package_name,
            feature_id=item.feature_id,
            daily_limit_minutes=item.daily_limit_minutes,
            block_mode=item.block_mode,
            enabled=item.enabled,
            schedule_json=item.schedule_json,
            metadata_json=item.metadata_json,
            lock_mode=item.lock_mode,
            lock_until_day_end_millis=item.lock_until_day_end_millis,
            lock_until_custom_millis=item.lock_until_custom_millis,
            lock_on_block_active=item.lock_on_block_active,
            lock_delay_minutes=item.lock_delay_minutes,
            lock_delay_started_at_millis=item.lock_delay_started_at_millis,
            source=source or item.source or "DASHBOARD",
            updated_at=updated_at,
            applied_at=applied_at,
        )
        db.add(policy)
        return policy

    apply_policy_item(policy, item, source=source or policy.source or item.source)
    policy.updated_at = updated_at
    if applied_at is not None:
        policy.applied_at = applied_at
    return policy


def summarize_policy_change(item: PolicyItem) -> str:
    target = item.package_name or item.feature_id or item.id
    mode = item.block_mode or "TIME_LIMIT"
    state = "enabled" if item.enabled else "disabled"
    strict = "strict" if item.lock_mode == "STRICT" else "normal"
    return f"{target}: {mode}, {state}, {strict}"


def add_device_change_event(db: Session, device: Device, summary: str, detail: dict) -> None:
    db.add(
        DeviceChangeEvent(
            device_id=device.id,
            event_type="POLICY_UPDATED",
            summary=summary,
            detail_json=json.dumps(detail, ensure_ascii=False),
        )
    )


def feature_to_item(feature: FeatureControl) -> FeatureControlItem:
    return FeatureControlItem(
        feature_id=feature.feature_id,
        enabled=feature.enabled,
        metadata_json=feature.metadata_json,
        source=feature.source or "DASHBOARD",
        updated_at=feature.updated_at,
        applied_at=feature.applied_at,
    )


def feature_differs_from_item(
    feature: FeatureControl,
    item: FeatureControlItem,
    source: str | None = None,
) -> bool:
    expected_source = source or feature.source or item.source
    return (
        feature.enabled != item.enabled
        or feature.metadata_json != item.metadata_json
        or feature.source != expected_source
    )


def upsert_feature_for_device(
    db: Session,
    device: Device,
    item: FeatureControlItem,
    source: str | None,
    updated_at: datetime,
    applied_at: datetime | None = None,
) -> FeatureControl:
    feature = (
        db.query(FeatureControl)
        .filter(
            FeatureControl.device_id == device.id,
            FeatureControl.feature_id == item.feature_id,
        )
        .first()
    )
    if not feature:
        feature = FeatureControl(
            device_id=device.id,
            feature_id=item.feature_id,
            enabled=item.enabled,
            metadata_json=item.metadata_json,
            source=source or item.source or "DASHBOARD",
            updated_at=updated_at,
            applied_at=applied_at,
        )
        db.add(feature)
        return feature

    feature.enabled = item.enabled
    feature.metadata_json = item.metadata_json
    feature.source = source or feature.source or item.source
    feature.updated_at = updated_at
    if applied_at is not None:
        feature.applied_at = applied_at
    return feature


def summarize_feature_change(item: FeatureControlItem) -> str:
    state = "enabled" if item.enabled else "disabled"
    return f"{item.feature_id}: {state}"


def add_feature_change_event(db: Session, device: Device, item: FeatureControlItem) -> None:
    db.add(
        DeviceChangeEvent(
            device_id=device.id,
            event_type="FEATURE_UPDATED",
            summary=f"Dashboard changed {summarize_feature_change(item)}",
            detail_json=json.dumps(
                {
                    "featureId": item.feature_id,
                    "enabled": item.enabled,
                    "metadataJson": item.metadata_json,
                },
                ensure_ascii=False,
            ),
        )
    )


@app.on_event("startup")
def on_startup() -> None:
    init_db()


@app.post("/api/v1/pairing/start", response_model=PairingStartResponse)
def start_pairing(payload: PairingStartRequest, db: Annotated[Session, Depends(get_db)]):
    device_token = payload.device_token.strip().lower()
    if not device_token:
        raise HTTPException(status_code=400, detail="Device token is required")

    now = datetime.utcnow()
    existing = (
        db.query(PairingSession)
        .filter(
            PairingSession.device_token == device_token,
            PairingSession.status == "PENDING",
            PairingSession.expires_at > now,
        )
        .order_by(PairingSession.created_at.desc())
        .first()
    )
    if existing:
        return {
            "pairing_code": format_pairing_code(existing.pairing_code),
            "expires_at": existing.expires_at,
            "status": existing.status,
        }

    code = generate_pairing_code(db)
    session = PairingSession(
        pairing_code=code,
        device_token=device_token,
        device_name=payload.device_name.strip() or "Android Device",
        expires_at=now + timedelta(minutes=10),
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return {
        "pairing_code": format_pairing_code(session.pairing_code),
        "expires_at": session.expires_at,
        "status": session.status,
    }


@app.get("/api/v1/pairing/status", response_model=PairingStatusResponse)
def pairing_status(
    db: Annotated[Session, Depends(get_db)],
    pairing_code: str | None = None,
    pairingCode: str | None = None,
    device_token: str | None = None,
    deviceToken: str | None = None,
):
    code_value = pairingCode or pairing_code
    token_value = deviceToken or device_token
    if not code_value or not token_value:
        raise HTTPException(status_code=400, detail="Pairing code and device token are required")

    normalized_code = normalize_pairing_code(code_value)
    normalized_token = token_value.strip().lower()
    session = (
        db.query(PairingSession)
        .filter(
            PairingSession.pairing_code == normalized_code,
            PairingSession.device_token == normalized_token,
        )
        .order_by(PairingSession.created_at.desc())
        .first()
    )
    if not session:
        return {"status": "UNKNOWN"}

    if session.status == "PENDING" and session.expires_at <= datetime.utcnow():
        session.status = "EXPIRED"
        db.commit()

    return {
        "status": session.status,
        "pairing_code": format_pairing_code(session.pairing_code),
        "expires_at": session.expires_at,
        "device_id": session.device_id if session.status == "PAIRED" else None,
        "device_secret": session.device_secret if session.status == "PAIRED" else None,
    }


@app.post("/api/v1/auth/register", response_model=TokenResponse)
def register(payload: RegisterRequest, db: Annotated[Session, Depends(get_db)]):
    if db.query(User).filter(User.email == payload.email).first():
        raise HTTPException(status_code=400, detail="Email already registered")
    user = User(email=payload.email, password_hash=pwd_context.hash(payload.password))
    db.add(user)
    db.commit()
    return TokenResponse(access_token=create_access_token(user.email))


@app.post("/api/v1/auth/login", response_model=TokenResponse)
def login(form: Annotated[OAuth2PasswordRequestForm, Depends()], db: Annotated[Session, Depends(get_db)]):
    user = db.query(User).filter(User.email == form.username).first()
    if not user or not pwd_context.verify(form.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    return TokenResponse(access_token=create_access_token(user.email))


@app.post("/api/v1/auth/local-dashboard", response_model=TokenResponse)
def local_dashboard_login(request: Request, db: Annotated[Session, Depends(get_db)]):
    client_host = request.client.host if request.client else ""
    if client_host not in {"127.0.0.1", "::1"}:
        raise HTTPException(status_code=403, detail="Local dashboard login is only available from this PC")
    user = get_or_create_local_dashboard_user(db)
    return TokenResponse(access_token=create_access_token(user.email))


@app.get("/api/v1/dashboard/connection-info", response_model=DashboardConnectionInfoResponse)
def dashboard_connection_info(
    request: Request,
    user: Annotated[User, Depends(get_current_user)],
):
    port = request.url.port or (443 if request.url.scheme == "https" else 80)
    phone_urls = detect_phone_backend_urls(port)
    return {
        "local_url": f"{request.url.scheme}://127.0.0.1:{port}",
        "recommended_phone_url": phone_urls[0] if phone_urls else None,
        "phone_urls": phone_urls,
    }


@app.get("/api/v1/devices", response_model=list[DeviceResponse])
def list_devices(user: Annotated[User, Depends(get_current_user)], db: Annotated[Session, Depends(get_db)]):
    devices = db.query(Device).filter(Device.user_id == user.id).all()
    return [
        {
            "id": d.id,
            "name": d.name,
            "pairing_code": d.pairing_code,
            "last_sync_at": d.last_sync_at,
            "paired_at": d.paired_at,
        }
        for d in devices
    ]


@app.post("/api/v1/devices/register", response_model=DeviceResponse)
def register_device(
    payload: DeviceRegisterRequest,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    normalized = normalize_pairing_code(payload.pairing_code)
    session = (
        db.query(PairingSession)
        .filter(PairingSession.pairing_code == normalized)
        .order_by(PairingSession.created_at.desc())
        .first()
    )
    if session:
        if session.status == "PENDING" and session.expires_at <= datetime.utcnow():
            session.status = "EXPIRED"
            db.commit()
        if session.status != "PENDING":
            raise HTTPException(status_code=400, detail=f"Pairing session is {session.status.lower()}")

        now = datetime.utcnow()
        device_secret = generate_device_secret()
        device = db.query(Device).filter(Device.device_token == session.device_token).first()
        if device:
            device.user_id = user.id
            device.name = payload.name or session.device_name
            device.pairing_code = normalized
            device.device_secret = device_secret
            device.paired_at = now
        else:
            device = Device(
                user_id=user.id,
                name=payload.name or session.device_name,
                device_token=session.device_token,
                device_secret=device_secret,
                pairing_code=normalized,
                paired_at=now,
            )
            db.add(device)
            db.flush()
        session.status = "PAIRED"
        session.approved_at = now
        session.device_id = device.id
        session.device_secret = device_secret
        db.commit()
        db.refresh(device)
        return {
            "id": device.id,
            "name": device.name,
            "pairing_code": device.pairing_code,
            "last_sync_at": device.last_sync_at,
            "paired_at": device.paired_at,
        }

    device = db.query(Device).filter(Device.pairing_code == normalized).first()
    if device:
        device.user_id = user.id
        device.name = payload.name
    else:
        device = Device(
            user_id=user.id,
            name=payload.name,
            device_token=normalized.lower(),
            pairing_code=normalized,
        )
        db.add(device)
    db.commit()
    db.refresh(device)
    return {
        "id": device.id,
        "name": device.name,
        "pairing_code": device.pairing_code,
        "last_sync_at": device.last_sync_at,
        "paired_at": device.paired_at,
    }


@app.get("/api/v1/devices/{device_id:int}/policies", response_model=list[PolicyItem])
def get_policies(
    device_id: int,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    return [policy_to_item(p) for p in device.policies]


@app.put("/api/v1/devices/{device_id:int}/policies")
def update_policies(
    device_id: int,
    payload: PoliciesUpdateRequest,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    now = datetime.utcnow()
    changed_count = 0
    for item in payload.policies:
        existing = (
            db.query(Policy)
            .filter(Policy.device_id == device.id, Policy.id == item.id)
            .first()
        )
        source = existing.source if existing else (item.source or "DASHBOARD")
        if existing and not policy_differs_from_item(existing, item, source=source):
            continue
        policy = upsert_policy_for_device(db, device, item, source=source, updated_at=now)
        policy.applied_at = None
        changed_count += 1
        add_device_change_event(
            db,
            device,
            summary=f"Dashboard changed {summarize_policy_change(item)}",
            detail={
                "policyId": item.id,
                "packageName": item.package_name,
                "blockMode": item.block_mode,
                "enabled": item.enabled,
                "lockMode": item.lock_mode,
            },
        )
    db.commit()
    return {"updated": changed_count}


@app.get("/api/v1/devices/{device_id:int}/apps", response_model=list[InstalledAppItem])
def get_device_apps(
    device_id: int,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    rows = (
        db.query(InstalledApp)
        .filter(InstalledApp.device_id == device.id)
        .order_by(InstalledApp.label.asc())
        .all()
    )
    return [{"package_name": row.package_name, "label": row.label} for row in rows]


@app.get("/api/v1/devices/{device_id:int}/features", response_model=list[FeatureControlItem])
def get_features_for_dashboard(
    device_id: int,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    rows = (
        db.query(FeatureControl)
        .filter(FeatureControl.device_id == device.id)
        .order_by(FeatureControl.feature_id.asc())
        .all()
    )
    return [feature_to_item(row) for row in rows]


@app.put("/api/v1/devices/{device_id:int}/features")
def update_features_for_dashboard(
    device_id: int,
    payload: FeatureControlsUpdateRequest,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    now = datetime.utcnow()
    changed_count = 0
    for item in payload.features:
        existing = (
            db.query(FeatureControl)
            .filter(
                FeatureControl.device_id == device.id,
                FeatureControl.feature_id == item.feature_id,
            )
            .first()
        )
        source = existing.source if existing else (item.source or "DASHBOARD")
        if existing and not feature_differs_from_item(existing, item, source=source):
            continue
        feature = upsert_feature_for_device(db, device, item, source=source, updated_at=now)
        feature.applied_at = None
        changed_count += 1
        add_feature_change_event(db, device, item)
    db.commit()
    return {"updated": changed_count}


@app.get("/api/v1/devices/{device_id:int}/changes", response_model=list[DeviceChangeResponse])
def get_dashboard_changes_for_dashboard(
    device_id: int,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    rows = (
        db.query(DeviceChangeEvent)
        .filter(DeviceChangeEvent.device_id == device.id)
        .order_by(DeviceChangeEvent.created_at.desc(), DeviceChangeEvent.id.desc())
        .limit(30)
        .all()
    )
    return [
        {
            "id": row.id,
            "event_type": row.event_type,
            "summary": row.summary,
            "detail_json": row.detail_json,
            "created_at": row.created_at,
        }
        for row in rows
    ]


@app.get("/api/v1/devices/policies", response_model=list[PolicyItem])
def get_policies_for_device_token(
    db: Annotated[Session, Depends(get_db)],
    x_device_secret: Annotated[str | None, Header(alias="X-Device-Secret")] = None,
    device_token: str | None = None,
    deviceToken: str | None = None,
):
    token_value = deviceToken or device_token
    if not token_value:
        raise HTTPException(status_code=400, detail="Device token is required")
    device = get_device_by_token(db, token_value, x_device_secret)
    return [policy_to_item(p) for p in device.policies]


@app.get("/api/v1/devices/features", response_model=list[FeatureControlItem])
def get_features_for_device_token(
    db: Annotated[Session, Depends(get_db)],
    x_device_secret: Annotated[str | None, Header(alias="X-Device-Secret")] = None,
    device_token: str | None = None,
    deviceToken: str | None = None,
):
    token_value = deviceToken or device_token
    if not token_value:
        raise HTTPException(status_code=400, detail="Device token is required")
    device = get_device_by_token(db, token_value, x_device_secret)
    rows = (
        db.query(FeatureControl)
        .filter(FeatureControl.device_id == device.id)
        .order_by(FeatureControl.feature_id.asc())
        .all()
    )
    return [feature_to_item(row) for row in rows]


@app.post("/api/v1/devices/sync/usage")
def sync_usage(
    payload: UsageSyncRequest,
    db: Annotated[Session, Depends(get_db)],
    x_device_secret: Annotated[str | None, Header(alias="X-Device-Secret")] = None,
):
    device = get_device_by_token(db, payload.device_token, x_device_secret)
    db.query(UsageSnapshot).filter(
        UsageSnapshot.device_id == device.id,
        UsageSnapshot.date_key == payload.date_key,
    ).delete()
    for entry in payload.entries:
        db.add(
            UsageSnapshot(
                device_id=device.id,
                date_key=payload.date_key,
                package_name=entry.package_name,
                used_millis=entry.used_millis,
            )
        )
    device.last_sync_at = datetime.utcnow()
    db.commit()
    return {"status": "ok"}


@app.post("/api/v1/devices/sync/state")
def sync_device_state(
    payload: DeviceStateSyncRequest,
    db: Annotated[Session, Depends(get_db)],
    x_device_secret: Annotated[str | None, Header(alias="X-Device-Secret")] = None,
):
    device = get_device_by_token(db, payload.device_token, x_device_secret)
    now = datetime.utcnow()

    db.query(InstalledApp).filter(InstalledApp.device_id == device.id).delete()
    for app_item in payload.installed_apps:
        if not app_item.package_name:
            continue
        db.add(
            InstalledApp(
                device_id=device.id,
                package_name=app_item.package_name,
                label=app_item.label or app_item.package_name,
                updated_at=now,
            )
        )

    for item in payload.policies:
        source = item.source or "PHONE"
        upsert_policy_for_device(
            db,
            device,
            item,
            source=source,
            updated_at=item.updated_at or now,
            applied_at=now,
        )

    for item in payload.features:
        source = item.source or "PHONE"
        upsert_feature_for_device(
            db,
            device,
            item,
            source=source,
            updated_at=item.updated_at or now,
            applied_at=now,
        )

    device.last_sync_at = now
    db.commit()
    return {
        "status": "ok",
        "apps": len(payload.installed_apps),
        "policies": len(payload.policies),
        "features": len(payload.features),
    }


@app.get("/api/v1/devices/changes", response_model=list[DeviceChangeResponse])
def get_dashboard_changes_for_device(
    db: Annotated[Session, Depends(get_db)],
    x_device_secret: Annotated[str | None, Header(alias="X-Device-Secret")] = None,
    device_token: str | None = None,
    deviceToken: str | None = None,
):
    token_value = deviceToken or device_token
    if not token_value:
        raise HTTPException(status_code=400, detail="Device token is required")
    device = get_device_by_token(db, token_value, x_device_secret)
    rows = (
        db.query(DeviceChangeEvent)
        .filter(DeviceChangeEvent.device_id == device.id)
        .order_by(DeviceChangeEvent.created_at.desc(), DeviceChangeEvent.id.desc())
        .limit(20)
        .all()
    )
    return [
        {
            "id": row.id,
            "event_type": row.event_type,
            "summary": row.summary,
            "detail_json": row.detail_json,
            "created_at": row.created_at,
        }
        for row in rows
    ]


@app.get("/api/v1/devices/{device_id:int}/usage", response_model=list[UsageSnapshotResponse])
def get_usage(device_id: int, user: Annotated[User, Depends(get_current_user)], db: Annotated[Session, Depends(get_db)]):
    device = get_device_for_user(db, user, device_id)
    rows = db.query(UsageSnapshot).filter(UsageSnapshot.device_id == device.id).all()
    return [
        {
            "date_key": row.date_key,
            "package_name": row.package_name,
            "used_millis": row.used_millis,
            "synced_at": row.synced_at,
        }
        for row in rows
    ]


@app.post("/api/v1/devices/{device_id:int}/unlock", response_model=UnlockResponse)
def emergency_unlock(
    device_id: int,
    payload: UnlockRequest,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    granted_until = datetime.utcnow() + timedelta(minutes=payload.duration_minutes)
    db.add(
        UnlockGrant(
            device_id=device.id,
            package_name=payload.package_name,
            source="LAPTOP",
            granted_until=granted_until,
        )
    )
    db.commit()
    return {"granted_until": granted_until}


@app.get("/api/v1/devices/{device_id:int}/status", response_model=DeviceStatusResponse)
def device_status(device_id: int, user: Annotated[User, Depends(get_current_user)], db: Annotated[Session, Depends(get_db)]):
    device = get_device_for_user(db, user, device_id)
    online = device.last_sync_at and device.last_sync_at > datetime.utcnow() - timedelta(minutes=20)
    return {
        "device_id": device.id,
        "online": bool(online),
        "last_sync_at": device.last_sync_at,
    }


@app.post("/api/v1/devices/{device_id:int}/override-events")
def log_override_event(
    device_id: int,
    override_type: str,
    active: bool,
    user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    device = get_device_for_user(db, user, device_id)
    db.add(
        OverrideEvent(
            device_id=device.id,
            override_type=override_type,
            active=active,
            until=None,
        )
    )
    db.commit()
    return {"status": "logged"}
