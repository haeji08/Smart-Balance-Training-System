from flask import Flask, request, jsonify, Response, stream_with_context
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from models import db, Train, CopPattern, SustainTime # models.py에서 db와 Train을 임포트
from datetime import datetime
import random, json, time, threading
import serial
from graphs import generate_cop_distribution_graph, generate_horizontal_balance_ratio_graph
from calculate import COPCalculator, SustainTimeCalculator


# 아두이노 연결 제외한 코드
app = Flask(__name__)

ser = serial.Serial('/dev/cu.usbmodem21201', 9600)  # 아두이노가 연결된 직렬 포트
train_started = False
train_ended = False
# 전역 변수 및 스레드 락
latest_sensor_data = {"S1": 0, "S2": 0, "S3": 0, "S4": 0}
sensor_lock = threading.Lock()

# PostgreSQL 연결 URL (SQLAlchemy 사용)
app.config['SQLALCHEMY_DATABASE_URI'] = 'postgresql://smart:qwerty@localhost/smartboard'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# 데이터베이스 초기화
db.init_app(app)  # db 객체를 Flask 앱과 연결
migrate = Migrate(app, db)

# 아두이노 데이터를 지속적으로 읽는 함수
def continuously_read_sensor_data():
    global latest_sensor_data
    while True:
        try:
            if ser.in_waiting > 0:
                data = ser.readline().decode('utf-8').strip()
                sensor_data = json.loads(data)
                with sensor_lock:
                    latest_sensor_data = sensor_data
                print(f"[Sensor Update] {latest_sensor_data}")
        except Exception as e:
            print(f"Error reading from Arduino: {e}")
        time.sleep(0.1)  # 읽기 간격

# 센서 데이터를 반환하는 함수
def parse_data():
    global latest_sensor_data
    with sensor_lock:
        return latest_sensor_data

# 센서 데이터를 읽는 스레드 시작
sensor_thread = threading.Thread(target=continuously_read_sensor_data)
sensor_thread.daemon = True
sensor_thread.start()

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
    
    while not train_ended:
        sensor_data = parse_data()
            
        # 각 방향의 값 가져오기
        F_u = sensor_data.get("S1", 0)
        F_d = sensor_data.get("S2", 0)
        F_r = sensor_data.get("S3", 0)
        F_l = sensor_data.get("S4", 0)
        print(f"check_eligibility sensor_data: {sensor_data}")

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
    parsed_data = parse_data()
    S1 = parsed_data.get("S1")
    S2 = parsed_data.get("S2")
    S3 = parsed_data.get("S3")
    S4 = parsed_data.get("S4")

    # COP 계산 (사용자 정의 함수)
    x_cop, y_cop = cop_calculator.get_cop(parsed_data)
 
    print(f"training sensor_data: {parsed_data}")
    print(f"x_cop: {x_cop}, y_cop: {y_cop}")

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
        "isInside": is_inside,
        "sensor": {
            "s1": S1,
            "s2": S2,
            "s3": S3,
            "s4": S4
        }
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

@app.route("/api/training/endTrainDetails", methods=["POST"])
def end_training():
    data = request.get_json()

    # 요청 데이터에서 trainId와 feedback 추출
    train_id = data.get("trainId")
    end_time = data.get("endTime")
    unbalance_count = data.get("unbalanceCount")

    if not train_id or not end_time or not unbalance_count:
        return jsonify({"error": "trainId and endTime are required"}), 400
    
    # Train 객체 가져오기
    train = Train.query.get(train_id)
    if not train:
        return jsonify({"error": "Train not found"}), 404
    # 기본 데이터베이스 업데이트 (end_time, total_balance_sustain_time)
    train.end_time = end_time
    train.total_unbalance_count = unbalance_count
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

                # cop_pattern 그래프 생성 및 데이터베이스 업데이트
                cop_graph_path = generate_cop_distribution_graph(train_id)
                train.cop_pattern = cop_graph_path
                
                # horizontal_balance_ratio 그래프 생성 및 데이터베이스 업데이트
                balance_graph_path = generate_horizontal_balance_ratio_graph(train_id)
                train.horizontal_balance_ratio = balance_graph_path
                
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
    is_cop_graph_ready = train.cop_pattern is not None 
    is_balance_graph_ready = train.horizontal_balance_ratio is not None
    is_graph_ready = is_cop_graph_ready and is_balance_graph_ready
    
    print(is_cop_graph_ready, is_balance_graph_ready)
    
    return jsonify({
        "trainId": train_id,
        "isGraphReady": is_graph_ready,
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
    balance_ratio_url = f"http://{request.host}/static/balance_ratios/train_{train_id}_horizontal_balance_ratio.png" if train.horizontal_balance_ratio else None

    response = train.to_dict()
    response["copPattern"] = cop_pattern_url
    response["horizontalBalanceRatio"] = balance_ratio_url
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
