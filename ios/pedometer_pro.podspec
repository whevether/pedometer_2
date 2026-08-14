#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint pedometer_pro.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'pedometer_pro'
  s.version          = '0.1.0'
  s.summary          = 'Pedometer & Step Detection. Get step count in a timeRange & stream live steps.'
  s.description      = <<-DESC
A Flutter plugin for step counting and pedestrian status on Android and iOS.
                       DESC
  s.homepage         = 'https://github.com/whevether/pedometer_pro'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'pedometer_pro/Sources/pedometer_pro/**/*.swift'
  s.dependency 'Flutter'
  s.platform = :ios, '13.0'
  s.swift_version = '5.0'

  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
end
