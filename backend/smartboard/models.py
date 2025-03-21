from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()


class Train(db.Model):
    __tablename__ = 'train'  # 테이블 이름

    id = db.Column(db.BigInteger, primary_key =True, autoincrement=True)  # Primary Key
    date = db.Column(db.Date, nullable=False)  # 날짜 정보
    start_time = db.Column(db.Time, nullable=False)  # 훈련 시작 시간
    end_time = db.Column(db.Time, nullable=True)  # 훈련 종료 시간
    total_balance_sustain_time = db.Column(db.Integer, nullable=True)  # 총 균형 유지 시간
    cop_pattern = db.Column(db.String, nullable=True)  # 체중 이동 패턴 시각화 이미지 경로
    total_unbalance_count = db.Column(db.Integer, nullable=True)  # 총 흔들림 횟수
    horizontal_balance_ratio = db.Column(db.String, nullable=True)  # 좌우 균형비율 시각화 이미지 경로
    self_feedback = db.Column(db.Text, nullable=True)  # 자가 피드백

    def __repr__(self):
        return f'<Train {self.id}>'
    
    def to_train_dict(self):
        return {
            'id': self.id,
            'date': self.date.strftime('%m/%d'),  # MM/DD 형식으로 변환
            'startTime': str(self.start_time),  # 시간은 문자열로 변환
            'endTime': str(self.end_time)
        }
        
    def to_dict(self):
        return {
            "id": self.id,
            "date": self.date.strftime('%m/%d'),
            "startTime": str(self.start_time),
            "endTime": str(self.end_time),
            "totalBalanceSustainTime": self.total_balance_sustain_time,
            "copPattern": self.cop_pattern,
            "totalUnbalanceCount": self.total_unbalance_count,
            "horizontalBalanceRatio": self.horizontal_balance_ratio,
            "selfFeedback": self.self_feedback,
        }
    
# 훈련 중 실시간 데이터 수집 목적 테이블
    
class CopPattern(db.Model):
    __tablename__ = 'cop_pattern'  # 테이블 이름

    id = db.Column(db.BigInteger, primary_key=True, autoincrement=True)  # Primary Key: 큰 정수
    train_id = db.Column(db.BigInteger, db.ForeignKey('train.id'), nullable=False)  # Train 테이블의 id를 외래키로
    timestamp = db.Column(db.DateTime, nullable=False)  # 실시간 시간 기록 (DateTime 타입)
    x_cop = db.Column(db.Float, nullable=False)  # 좌우 체중중심 (숫자형, 소수 포함 가능)
    y_cop = db.Column(db.Float, nullable=False)  # 위아래 체중중심 (숫자형, 소수 포함 가능)

    def __repr__(self):
        return f'<CopPattern {self.id}>'
    
class SustainTime(db.Model):
    __tablename__ = 'sustain_time'  # 테이블 이름

    id = db.Column(db.BigInteger, primary_key=True, autoincrement=True)  # Primary Key: 큰 정수
    train_id = db.Column(db.BigInteger, db.ForeignKey('train.id'), nullable=False)  # Train 테이블의 id를 외래키로
    timestamp = db.Column(db.DateTime, nullable=False)  # 실시간 시간 기록 (DateTime 타입)
    balance_sustain_time = db.Column(db.Float, nullable=False)  # 균형 유지 시간 (초 단위, 숫자형)

    def __repr__(self):
        return f'<SustainTime {self.id}>'
    
# 마이페이지 분석 결과 테이블
class MyPage(db.Model):
    __tablename__ = 'mypage' 
    
    id = db.Column(db.BigInteger, primary_key=True, autoincrement=True)  # Primary Key: 큰 정수
    year = db.Column(db.Integer, nullable=False)  # 연도 정보
    month = db.Column(db.Integer, nullable=False)  # 월 정보
    week = db.Column(db.Integer, nullable=False)  # 주차 정보
    balance_sustain_time = db.Column(db.Float, nullable=False)  # 균형 유지 시간 (초 단위, 숫자형)
    horizontal_balance_ratio = db.Column(db.Float, nullable=False)  # 좌우 균형비율 (0~100, 퍼센트)
    unbalance_count = db.Column(db.Integer, nullable=False)  # 균형 흔들림 횟수 (정수형)
    
    def __repr__(self):
        return f'<MyPage {self.id}>'
    
    