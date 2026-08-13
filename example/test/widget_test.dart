import 'package:flutter_test/flutter_test.dart';
import 'package:pedometer_pro_example/main.dart';

void main() {
  testWidgets('example app loads', (WidgetTester tester) async {
    await tester.pumpWidget(const PedometerProExampleApp());
    expect(find.text('Pedometer Pro'), findsOneWidget);
    expect(find.text('Request permission'), findsOneWidget);
  });
}
