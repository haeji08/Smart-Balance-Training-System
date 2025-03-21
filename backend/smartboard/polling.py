from flask import Flask, request, jsonify, Response, stream_with_context
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from models import db, Train, CopPattern, SustainTime # models.py에서 db와 Train을 임포트
from datetime import datetime
import random, json, time
from calculate import COPCalculator, SustainTimeCalculator


# 아두이노 연결 제외한 코드
app = Flask(__name__)

# PostgreSQL 연결 URL (SQLAlchemy 사용)
app.config['SQLALCHEMY_DATABASE_URI'] = 'postgresql://smart:qwerty@localhost/smartboard'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# 데이터베이스 초기화
db.init_app(app)  # db 객체를 Flask 앱과 연결
migrate = Migrate(app, db)

# 기본 경로
@app.route("/")
def home():
    return "Hello, PostgreSQL!"

# 훈련 데이터 요청 경로
@app.route("/api/train", methods=["GET"])
def get_train_data():
    # 쿼리 파라미터로 받은 'date' 값 가져오기 (YYYY/MM/DD 형식)
    date = request.args.get("date")  # 예: "2024/10/23"

    if not date:
        return jsonify({"error": "Date parameter is required"}), 400

    # 데이터베이스에서 해당 날짜의 훈련 데이터를 조회
    train_data = Train.query.filter_by(date=date).all()

    # 훈련 데이터가 없으면 빈 리스트 반환
    if not train_data:
        return jsonify([]), 200

    # 훈련 데이터를 JSON으로 변환하여 반환
    result = [train.to_train_dict() for train in train_data]

    return jsonify(result), 200

@app.route("/api/train/start", methods=["POST"])
def start_train():
    # 오늘 날짜와 현재 시간 가져오기
    current_date = datetime.now().date()
    start_time = datetime.now().time().strftime("%H:%M:%S")

    # Train 테이블에 새 데이터 생성
    new_train = Train(date=current_date, start_time=start_time)
    db.session.add(new_train)
    db.session.commit()

    return jsonify({
        "trainId": new_train.id
    }), 201
  
@app.route("/api/training", methods=["GET"])
def training():
    train_id = request.args.get("id")
    if not train_id:
        return jsonify({"error": "Train ID is required"}), 400
    
    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404

    # 센서 데이터 가져오기 (아두이노에서 데이터 읽기)
    # 데이터를 JSON으로 변환
    cop_calculator = COPCalculator()
    parsed_data = {
                "up": random.randint(10, 100),
                "down": random.randint(10, 100),
                "left": random.randint(10, 100),
                "right": random.randint(10, 100),
                }

    # COP 계산 (사용자 정의 함수)
    x_cop, y_cop = cop_calculator.get_cop(parsed_data)

    # # CopPattern 테이블에 저장
    # cop_pattern = CopPattern(
    #     train_id = train.id,
    #     timestamp = datetime.now(),
    #     x_cop = x_cop,
    #     y_cop = y_cop
    # )
    # db.session.add(cop_pattern)
    
    sustain_calculator = SustainTimeCalculator()
    is_inside = sustain_calculator.check_balance(x_cop, y_cop)

     # 센서 데이터를 응답으로 반환
    return jsonify({
        "copPattern": {
            "x_cop": x_cop,
            "y_cop": y_cop
        },
        "isInside": is_inside
    }), 200
    
@app.route("/api/training/sustainTime", methods=["POST"])
def inssert_sustain_time():
    data = request.get_json()

    # 요청 데이터에서 trainId와 feedback 추출
    train_id = data.get("trainId")
    sustainTime = data.get("sustainTime")

    if not train_id or not sustainTime:
        return jsonify({"error": "trainId and sustainTime are required"}), 400
    
    # Train 객체 가져오기
    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404

    # 훈련 데이터를 데이터베이스에 저장
    sustain_time = SustainTime(
        train_id=train.id,
        timestamp=datetime.now(),
        balance_sustain_time=sustainTime  # 예: balance_time 키 사용
    )
    db.session.add(sustain_time)
    db.session.commit()
    
    return jsonify({"message": "Feedback updated successfully"}), 200


@app.route("/api/train/details", methods=["GET"])
def get_train_details():
    train_id = request.args.get("id")
    if not train_id:
        return jsonify({"error": "Train ID is required"}), 400

    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404

    return jsonify(train.to_dict()), 200

@app.route("/api/train/feedback", methods=["POST"])
def submit_feedback():
    data = request.get_json()

    # 요청 데이터에서 trainId와 feedback 추출
    train_id = data.get("trainId")
    feedback = data.get("feedback")

    if not train_id or not feedback:
        return jsonify({"error": "trainId and feedback are required"}), 400
    
    print(train_id, feedback)
    
    # Train 객체 가져오기
    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404

    # Feedback 저장
    train.self_feedback = feedback
    db.session.commit()

    return jsonify({"message": "Feedback updated successfully"}), 200

if __name__ == "__main__":
    app.run(port=5001, debug=True)
