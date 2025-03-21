from flask import Flask, request, jsonify, Response, stream_with_context
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from models import db, Train, CopPattern, SustainTime # models.py에서 db와 Train을 임포트
from datetime import datetime
import random, json, time, threading
import serial
from graphs import generate_cop_distribution_graph
from calculate import COPCalculator, SustainTimeCalculator


# 아두이노 연결 제외한 코드
app = Flask(__name__)

# ser = serial.Serial('/dev/cu.usbmodem21201', 9600)  # 아두이노가 연결된 직렬 포트
train_started = False
train_ended = False
start_time_global = None

# PostgreSQL 연결 URL (SQLAlchemy 사용)
app.config['SQLALCHEMY_DATABASE_URI'] = 'postgresql://smart:qwerty@localhost/smartboard'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# 데이터베이스 초기화
db.init_app(app)  # db 객체를 Flask 앱과 연결
migrate = Migrate(app, db)

def parse_data():
    global start_time_global, train_started

    # 훈련 시작 후 초기 시간 설정
    if train_started and start_time_global is None:
        start_time_global = time.time()

    # 훈련 시작 전 (train_started가 False일 때), 0이 아닌 값 반환하여 훈련 시작 유도
    if not train_started:
        parsed_data = {
            "up": random.randint(0, 100),
            "down": random.randint(0, 100),
            "left": random.randint(0, 100),
            "right": random.randint(0, 100),
        }
    # 훈련 시작 후 10초 동안은 0이 아닌 값을 반환
    elif train_started and time.time() - start_time_global <= 10:
        parsed_data = {
            "up": random.randint(0, 100),
            "down": random.randint(0, 100),
            "left": random.randint(0, 100),
            "right": random.randint(0, 100),
        }
    else:
        # 10초 이후에는 모두 0을 반환하여 훈련 종료를 유도
        parsed_data = {
            "up": 0,
            "down": 0,
            "left": 0,
            "right": 0,
        }

    return parsed_data

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

# 훈련을 시작하는 조건을 체크하는 함수
def check_train_eligibility():
    global train_started, train_ended
    start_time = None  # 5초 이상 지속되는지 확인할 타이머
    end_time = None  # 훈련 종료를 위한 타이머
    
    while not train_ended:
        # if ser.in_waiting > 0:
        #     data = ser.readline().decode('utf-8').strip()
            # sensor_data = json.loads(data)  # JSON으로 파싱
            sensor_data = parse_data()
            
            # 각 방향의 값 가져오기
            F_u = sensor_data.get("up", 0)
            F_d = sensor_data.get("down", 0)
            F_r = sensor_data.get("right", 0)
            F_l = sensor_data.get("left", 0)
            print(F_u, F_d, F_r, F_l)

            # 0이 아닌 값이 하나라도 있으면 훈련 시작
            if any([F_u != 0, F_d != 0, F_r != 0, F_l != 0]):
                if start_time is None:
                    start_time = time.time()  # 타이머 시작
                elif time.time() - start_time >= 5:  # 5초 이상 유지되면 훈련 시작
                    if not train_started:
                        train_started = True
                        print("훈련이 시작되었습니다.")  # 훈련 시작 메시지 보내기
            else:
                start_time = None  # 값이 0이면 타이머 리셋

            # 훈련 중인 상태에서 5초 동안 센서 값이 0이 계속 유지되면 훈련 종료
            if train_started and all([F_u == 0, F_d == 0, F_r == 0, F_l == 0]):
                if end_time is None:
                    end_time = time.time()  # 종료 타이머 시작
                elif time.time() - end_time >= 5:  # 5초 이상 0이면 훈련 종료
                    if not train_ended:
                        train_ended = True
                        print("훈련이 종료되었습니다.")  # 훈련 종료 메시지 보내기
            else:
                end_time = None  # 값이 0이 아니면 종료 타이머 리셋
            
            time.sleep(1)
        
# 훈련 시작을 위한 API (안드로이드 요청 처리)
@app.route("/api/train/ready", methods=["POST"])
def ready_train():
    global train_started, train_ended
    # 훈련 시작 전에 상태를 리셋
    train_started = False
    train_ended = False

    # 훈련 시작을 백그라운드에서 실행
    thread = threading.Thread(target=check_train_eligibility)
    thread.daemon = True  # 메인 프로그램 종료 시 백그라운드 스레드도 종료되도록 설정
    thread.start()

    return jsonify({"message": "훈련 시작 조건 확인 중..."}), 200

# 훈련 시작, 종료를 위한 API
@app.route("/api/train/check", methods=["GET"])
def check_train_status():
    global train_started, train_ended
    if train_started and not train_ended:
        return jsonify({"message": "훈련이 시작되었습니다."}), 200
    elif train_ended:
        return jsonify({"message": "훈련이 종료되었습니다."}), 200
    else:
        return jsonify({"message": "훈련을 시작할 조건이 아닙니다."}), 400

@app.route("/api/train/start", methods=["POST"])
def start_train():
    # 오늘 날짜와 현재 시간 가져오기
    data = request.get_json()
    start_time = data.get("startTime")
    current_date = datetime.now().date()

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
    # parsed_data = {
    #             "up": random.randint(0, 3),
    #             "down": random.randint(0, 3),
    #             "left": random.randint(0, 3),
    #             "right": random.randint(0, 3),
    #             }
    parsed_data = parse_data()

    # COP 계산 (사용자 정의 함수)
    x_cop, y_cop = cop_calculator.get_cop(parsed_data)

    # CopPattern 테이블에 저장
    cop_pattern = CopPattern(
        train_id = train.id,
        timestamp = datetime.now(),
        x_cop = x_cop,
        y_cop = y_cop
    )
    db.session.add(cop_pattern)
    db.session.commit()
    
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
    
    return jsonify({"message": "SustainTime added successfully"}), 200

@app.route("/api/training/endTime", methods=["POST"])
def end_training():
    data = request.get_json()

    # 요청 데이터에서 trainId와 feedback 추출
    train_id = data.get("trainId")
    end_time = data.get("endTime")

    if not train_id or not end_time:
        return jsonify({"error": "trainId and endTime are required"}), 400
    
    # Train 객체 가져오기
    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404
    # 기본 데이터베이스 업데이트 (end_time, total_balance_sustain_time)
    train.end_time = end_time
    train.total_balance_sustain_time = calculate_total_balance_sustain_time(train_id)
    db.session.commit()

    # 비동기적으로 그래프 생성 및 DB 업데이트
    def process_graph(train_id):
        try:
            with app.app_context():  # Flask 애플리케이션 컨텍스트 설정
                train = Train.query.get(train_id)
                if not train:
                    print(f"Train ID {train_id} not found.")
                    return

                # 그래프 생성 및 데이터베이스 업데이트
                graph_path = generate_cop_distribution_graph(train_id)
                train.cop_pattern = graph_path
                db.session.commit()
                print(f"Graph generation completed for Train ID: {train_id}")
        except Exception as e:
            print(f"Error in graph generation for Train ID {train_id}: {e}")



    # 별도 스레드에서 실행
    thread = threading.Thread(target=process_graph, args=(train_id,))
    thread.start()
    
    return jsonify({"message": "endTime updated successfully"}), 200

def calculate_total_balance_sustain_time(train_id):
    # train_id에 해당하는 모든 SustainTime 레코드 조회
    sustain_times = SustainTime.query.filter_by(train_id=train_id).all()
    
    if not sustain_times:
        return 0  # 해당 train_id로 데이터가 없으면 0 반환
    
    # balance_sustain_time의 총합 계산
    total_balance_sustain_time = sum(st.balance_sustain_time for st in sustain_times)
    
    return total_balance_sustain_time

@app.route("/api/train/status", methods=["GET"])
def get_training_status():
    train_id = request.args.get("id")
    if not train_id:
        return jsonify({"error": "Train ID is required"}), 400

    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404

    # 그래프 경로가 설정되었는지 확인
    is_graph_ready = train.cop_pattern is not None
    return jsonify({
        "trainId": train_id,
        "isGraphReady": is_graph_ready
    }), 200

@app.route("/api/train/details", methods=["GET"])
def get_train_details():
    train_id = request.args.get("id")
    if not train_id:
        return jsonify({"error": "Train ID is required"}), 400

    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404

    # 정적 파일 URL 생성
    cop_pattern_url = f"http://{request.host}/static/cop_patterns/train_{train_id}_cop_distribution.png" if train.cop_pattern else None

    response = train.to_dict()
    response["copPattern"] = cop_pattern_url
    return jsonify(response), 200

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
