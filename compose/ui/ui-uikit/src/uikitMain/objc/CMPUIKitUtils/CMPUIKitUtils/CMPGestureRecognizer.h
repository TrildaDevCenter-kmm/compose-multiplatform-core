//
//  CMPGestureRecognizer.h
//  CMPUIKitUtils
//
//  Created by Ilia.Semenov on 28/06/2024.
//

#import <UIKit/UIKit.h>
#import <UIKit/UIGestureRecognizerSubclass.h>

NS_ASSUME_NONNULL_BEGIN

@protocol CMPGestureRecognizerHandler <NSObject>

- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent * _Nullable)event;
- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent * _Nullable)event;
- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent * _Nullable)event;
- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent * _Nullable)event;
- (BOOL)shouldRecognizeSimultaneously:(UIGestureRecognizer *)first withOther:(UIGestureRecognizer *)second;

@end

@interface CMPGestureRecognizer : UIGestureRecognizer <UIGestureRecognizerDelegate>

@property (weak, nonatomic) id <CMPGestureRecognizerHandler> handler;

@end

NS_ASSUME_NONNULL_END