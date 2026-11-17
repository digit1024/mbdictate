import 'package:flutter/material.dart';

import 'screens/home_screen.dart';

void main() {
  runApp(const MBDictateApp());
}

class MBDictateApp extends StatelessWidget {
  const MBDictateApp({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFF4F46E5),
      brightness: Brightness.dark,
    );

    return MaterialApp(
      title: 'mbdictate',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: colorScheme,
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
