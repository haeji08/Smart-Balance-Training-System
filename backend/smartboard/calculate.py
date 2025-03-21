import time

class COPCalculator:
    def __init__(self):
        self.previous_cop = (0, 0)  # 초기 COP 값

    def get_cop(self, sensor_data):
        # 센서 값 가져오기
        V1 = sensor_data.get("S1", 0)  # 왼쪽 앞꿈치
        V2 = sensor_data.get("S2", 0)  # 왼쪽 뒷꿈치
        V3 = sensor_data.get("S3", 0)  # 오른쪽 앞꿈치
        V4 = sensor_data.get("S4", 0)  # 오른쪽 뒷꿈치

        # 센서 값의 총합
        total_force = V1 + V2 + V3 + V4
        
        # print(f"V1: {V1}, V2: {V2}, V3: {V3}, V1: {V4}")
        # print(f"total_force: {total_force}")

        # 모든 센서 값이 0일 경우, 이전 COP 값 반환
        if total_force == 0:
            return self.previous_cop

        # x축 (좌↔우) 계산
        x_cop = ((V3 + V4) - (V1 + V2)) / total_force

        # y축 (앞↔뒤) 계산
        y_cop = ((V1 + V3) - (V2 + V4)) / total_force

        # COP 값을 업데이트
        self.previous_cop = (x_cop, y_cop)
        return x_cop, y_cop

    
class SustainTimeCalculator:
    def __init__(self, threshold=0.3):
        self.threshold = threshold  # 반경 임계값

    def check_balance(self, x_cop, y_cop):
        # COP가 반경 내에 있는지 확인
        is_within_radius = (x_cop ** 2 + y_cop ** 2) <= (self.threshold ** 2)
        return "YES" if is_within_radius else "NO"
